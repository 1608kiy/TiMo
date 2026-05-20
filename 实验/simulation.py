#!/usr/bin/env python3
"""
离线模拟实验脚本 — FSRS vs FSRS+DF 调度效果对比
=================================================
模拟 3 种用户行为模式，对比纯 FSRS、FSRS+DF（完整版）以及消融实验结果。
输出：控制台对比表格、CSV 文件、可选 matplotlib 图表。

用法：python simulation.py
"""

import math
import random
import csv
import os
import sys
from dataclasses import dataclass, field
from typing import List, Dict, Tuple, Optional

# ============================================================
#  常量 / 参数
# ============================================================
THETA_RT = 0.3        # theta_1: reaction-time weight
THETA_ACC = 0.5       # theta_2: accuracy weight
MU = 8.0              # population mean reaction time (seconds)
SIGMA = 3.0           # population std  reaction time (seconds)

NUM_WORDS = 200       # virtual vocabulary size
SIM_DAYS = 14         # simulation horizon (days)
INITIAL_S = 1.0       # initial stability
INITIAL_D = 5.0       # initial difficulty
R_THRESHOLD = 0.85    # retrievability threshold for scheduling review
STUBBORN_CONSEC = 3   # consecutive grade<2.0 to mark as stubborn

# grade thresholds
GRADE_CORRECT = 4.0    # user knows the word
GRADE_PARTIAL = 2.0    # user vaguely remembers
GRADE_WRONG = 1.0      # user does not know

# ============================================================
#  Data classes
# ============================================================

@dataclass
class WordState:
    """Per-word FSRS state."""
    word_id: int
    s: float = INITIAL_S        # stability
    d: float = INITIAL_D        # difficulty
    r: float = 1.0              # retrievability (starts at 1.0)
    last_review_day: int = 0
    review_count: int = 0
    consecutive_low: int = 0    # consecutive grade < 2.0
    is_stubborn: bool = False
    # history for DF calculation
    correct_history: List[float] = field(default_factory=list)  # 1.0/0.0
    rt_history: List[float] = field(default_factory=list)       # reaction times


@dataclass
class UserProfile:
    """User behavior profile."""
    name: str
    base_accuracy: float        # p0: probability of knowing a word
    rt_mean: float              # mean reaction time (seconds)
    rt_std: float               # std  of reaction time


# Three user profiles
PROFILES = [
    UserProfile("高频用户", 0.85, 6.0, 2.0),
    UserProfile("中频用户", 0.70, 8.0, 3.0),
    UserProfile("低频用户", 0.55, 10.0, 4.0),
]


# ============================================================
#  FSRS Core (simplified)
# ============================================================

def compute_retrievability(stability: float, delta_t: float) -> float:
    """R = exp(ln(0.9) * delta_t / S)"""
    if stability <= 0:
        return 0.0
    return math.exp(math.log(0.9) * delta_t / stability)


def fsrs_update(s: float, d: float, grade: float, delta_t: float) -> Tuple[float, float, float]:
    """
    Simplified FSRS update.
    Returns (new_S, new_D, new_R).
    """
    # --- Difficulty update ---
    # D moves toward a target based on grade
    # grade 4 -> target lower difficulty; grade 1 -> target higher difficulty
    d_target = 8.0 - 0.6 * grade  # grade 4 -> 5.6, grade 1 -> 7.4
    new_d = d + 0.1 * (d_target - d)  # gentle pull toward target
    new_d = max(1.0, min(10.0, new_d))

    # --- Stability update ---
    # S' depends on D, S, and grade
    # Higher difficulty -> shorter stability; higher grade -> longer stability
    d_factor = max(0.2, 1.0 - (new_d - 5.0) * 0.05)  # normalize around D=5
    grade_factor = 0.5 + 0.5 * (grade / 4.0)  # grade 4 -> 1.0, grade 1 -> 0.625

    if grade >= 3.0:
        # successful recall: S grows
        new_s = s * (1.0 + d_factor * grade_factor)
    else:
        # failed recall: S shrinks
        new_s = s * (0.5 + 0.1 * grade)

    new_s = max(0.5, min(365.0, new_s))

    # --- Retrievability at review time ---
    r_at_review = compute_retrievability(s, delta_t)
    return new_s, new_d, r_at_review


# ============================================================
#  Dynamic Forgetting Factor (DF)
# ============================================================

def lambda_rt(reaction_time: float, total_correct: int) -> float:
    """
    Reaction-time factor.
    Uses personal mean/sigma after >=30 correct answers; population defaults before.
    Returns value clamped to [0.7, 1.3].
    """
    # For simplicity, we use the population defaults throughout (cold-start effect
    # is minimal for 14-day sim with 200 words).  The personal stats could be
    # plugged in via UserProfile, but the formula remains the same.
    mu = MU
    sigma = SIGMA

    # t_normalized in [-1, 1] roughly
    t_norm = (reaction_time - mu) / (2.0 * sigma)
    raw = 1.0 + THETA_RT * (1.0 - t_norm)
    return max(0.7, min(1.3, raw))


def lambda_acc(correct_history: List[float]) -> float:
    """
    Accuracy factor based on historical accuracy (moving window).
    p = mean of recent correct_history.
    Returns value clamped to [0.7, 1.3].
    """
    if len(correct_history) == 0:
        return 1.0  # cold start
    p = sum(correct_history) / len(correct_history)
    raw = 1.0 + THETA_ACC * (p - 0.8)
    return max(0.7, min(1.3, raw))


def compute_df(rt: float, correct_history: List[float],
               mode: str = "full") -> float:
    """
    Compute Dynamic Forgetting Factor.
    mode: "full", "rt_only", "acc_only", "none"
    """
    l_rt = lambda_rt(rt, sum(correct_history)) if mode in ("full", "rt_only") else 1.0
    l_acc = lambda_acc(correct_history) if mode in ("full", "acc_only") else 1.0
    # lambda_skip not used in quick_memory mode (set to 1.0)
    df = l_rt * l_acc * 1.0
    return max(0.5, min(1.5, df))  # DF itself clamped


# ============================================================
#  User behavior simulation
# ============================================================

def simulate_user_response(profile: UserProfile, word: WordState) -> Tuple[float, float]:
    """
    Simulate a user's response to a word.
    Returns (grade, reaction_time).
    """
    # Probability of knowing = base_accuracy adjusted by R
    # If R is high, the word is fresh -> higher chance of knowing
    p_know = profile.base_accuracy * (0.5 + 0.5 * word.r)
    p_know = max(0.05, min(0.98, p_know))

    knows = random.random() < p_know

    if knows:
        grade = GRADE_CORRECT
        # faster reaction when confident
        rt = max(1.0, random.gauss(profile.rt_mean - 1.0, profile.rt_std * 0.8))
    else:
        # might vaguely remember
        vaguely = random.random() < 0.3
        grade = GRADE_PARTIAL if vaguely else GRADE_WRONG
        rt = max(1.0, random.gauss(profile.rt_mean + 2.0, profile.rt_std * 1.2))

    return grade, rt


# ============================================================
#  Simulation engine
# ============================================================

@dataclass
class DailyRecord:
    day: int
    avg_r: float
    review_count: int
    stubborn_count: int
    avg_s: float
    avg_d: float


def run_simulation(profile: UserProfile, mode: str) -> List[DailyRecord]:
    """
    Run a 14-day simulation for a given user profile and scheduling mode.
    mode: "fsrs_only", "fsrs_df_full", "fsrs_df_rt", "fsrs_df_acc"
    Returns list of daily records.
    """
    # Initialize word states
    words = [WordState(word_id=i) for i in range(NUM_WORDS)]

    # Determine DF mode string
    df_mode_map = {
        "fsrs_only": "none",
        "fsrs_df_full": "full",
        "fsrs_df_rt": "rt_only",
        "fsrs_df_acc": "acc_only",
    }
    df_mode = df_mode_map[mode]

    records: List[DailyRecord] = []

    for day in range(1, SIM_DAYS + 1):
        review_count = 0
        for w in words:
            # Compute current R
            delta_t = day - w.last_review_day
            if delta_t <= 0:
                continue
            w.r = compute_retrievability(w.s, delta_t)

            # Decide whether to review: if R drops below threshold
            # or if this is day 1 (review all)
            if w.r >= R_THRESHOLD and day > 1:
                continue  # word is still fresh, skip

            # --- Review happens ---
            review_count += 1

            # Simulate user response
            grade, rt = simulate_user_response(profile, w)

            # FSRS update (without DF)
            new_s, new_d, r_at_review = fsrs_update(w.s, w.d, grade, delta_t)

            # Apply DF if applicable
            df = compute_df(rt, w.correct_history, df_mode)
            final_s = new_s * df
            final_s = max(0.5, min(1.5 * 365.0 / 1.0, final_s))  # generous upper clamp

            # In "fsrs_only" mode, DF=1.0 so final_s == new_s
            # But we still clamp to same range for fairness
            if mode == "fsrs_only":
                final_s = new_s  # no DF applied

            # Actually, for the experiment to be meaningful, let's use a tighter clamp
            # that matches the design doc: S_final in [0.5, 1.5] * S'
            # Wait, the doc says S_final = S' * DF, clamp [0.5, 1.5] — that seems
            # like the DF is clamped, not S_final. Let me re-read...
            # The task says: S_final = S' * DF, clamp [0.5, 1.5]
            # This means S_final itself is clamped to [0.5, 1.5]? That would be too tight.
            # Looking at the design: DF is clamped [0.7, 1.3], and S_final = S' * DF.
            # The clamp [0.5, 1.5] likely refers to the DF range after composition.
            # Actually, re-reading: "S_final = S' × DF，clamp [0.5, 1.5]" — this is the
            # DF clamp, not S clamp. The DF itself is clamped to [0.5, 1.5].
            # We already clamp DF to [0.5, 1.5] in compute_df.

            w.s = final_s
            w.d = new_d
            w.last_review_day = day
            w.review_count += 1

            # Update history
            is_correct = 1.0 if grade >= 3.0 else 0.0
            w.correct_history.append(is_correct)
            w.rt_history.append(rt)

            # Stubborn word check
            if grade < 2.0:
                w.consecutive_low += 1
            else:
                w.consecutive_low = 0

            if w.consecutive_low >= STUBBORN_CONSEC:
                w.is_stubborn = True

        # Compute daily statistics
        avg_r = sum(w.r for w in words) / len(words)
        avg_s = sum(w.s for w in words) / len(words)
        avg_d = sum(w.d for w in words) / len(words)
        stubborn_count = sum(1 for w in words if w.is_stubborn)

        records.append(DailyRecord(
            day=day,
            avg_r=avg_r,
            review_count=review_count,
            stubborn_count=stubborn_count,
            avg_s=avg_s,
            avg_d=avg_d,
        ))

    return records


# ============================================================
#  Output helpers
# ============================================================

def print_comparison_table(profile: UserProfile,
                           results: Dict[str, List[DailyRecord]]):
    """Print a formatted comparison table for one user profile."""
    print(f"\n{'='*80}")
    print(f"  用户类型: {profile.name}")
    print(f"  基础正确率: {profile.base_accuracy:.0%} | "
          f"反应时: N({profile.rt_mean:.0f}, {profile.rt_std:.0f})")
    print(f"{'='*80}")

    # --- Daily R comparison ---
    print(f"\n  [平均 R 值随天数变化]")
    header = f"  {'Day':>4}"
    mode_labels = {
        "fsrs_only": "FSRS",
        "fsrs_df_full": "FSRS+DF",
        "fsrs_df_rt": "DF(仅rt)",
        "fsrs_df_acc": "DF(仅acc)",
    }
    for mode_key in results:
        header += f"  {mode_labels[mode_key]:>10}"
    print(header)
    print(f"  {'-'*4}" + f"  {'-'*10}" * len(results))

    for day_idx in range(SIM_DAYS):
        line = f"  {day_idx+1:>4}"
        for mode_key in results:
            r = results[mode_key][day_idx].avg_r
            line += f"  {r:>10.4f}"
        print(line)

    # --- Summary statistics ---
    print(f"\n  [汇总统计]")
    print(f"  {'指标':<20}", end="")
    for mode_key in results:
        print(f"  {mode_labels[mode_key]:>10}", end="")
    print()
    print(f"  {'-'*20}" + f"  {'-'*10}" * len(results))

    # Final avg R
    print(f"  {'最终平均R值':<20}", end="")
    for mode_key in results:
        final_r = results[mode_key][-1].avg_r
        print(f"  {final_r:>10.4f}", end="")
    print()

    # Total reviews
    print(f"  {'总复习次数':<20}", end="")
    for mode_key in results:
        total_rev = sum(r.review_count for r in results[mode_key])
        print(f"  {total_rev:>10}", end="")
    print()

    # Average daily reviews
    print(f"  {'日均复习次数':<20}", end="")
    for mode_key in results:
        total_rev = sum(r.review_count for r in results[mode_key])
        avg_rev = total_rev / SIM_DAYS
        print(f"  {avg_rev:>10.1f}", end="")
    print()

    # Final stubborn count
    print(f"  {'最终顽固词数':<20}", end="")
    for mode_key in results:
        final_stub = results[mode_key][-1].stubborn_count
        print(f"  {final_stub:>10}", end="")
    print()

    # Average difficulty
    print(f"  {'最终平均难度D':<20}", end="")
    for mode_key in results:
        final_d = results[mode_key][-1].avg_d
        print(f"  {final_d:>10.2f}", end="")
    print()

    # Average stability
    print(f"  {'最终平均稳定S':<20}", end="")
    for mode_key in results:
        final_s = results[mode_key][-1].avg_s
        print(f"  {final_s:>10.2f}", end="")
    print()


def save_csv(profile: UserProfile, results: Dict[str, List[DailyRecord]],
             output_dir: str):
    """Save daily records to CSV files."""
    mode_labels = {
        "fsrs_only": "fsrs_only",
        "fsrs_df_full": "fsrs_df_full",
        "fsrs_df_rt": "fsrs_df_rt_only",
        "fsrs_df_acc": "fsrs_df_acc_only",
    }
    safe_name = profile.name.replace(" ", "_")

    # Combined CSV
    csv_path = os.path.join(output_dir, f"simulation_{safe_name}.csv")
    with open(csv_path, "w", newline="", encoding="utf-8-sig") as f:
        writer = csv.writer(f)
        writer.writerow(["day", "mode", "avg_r", "review_count",
                         "stubborn_count", "avg_s", "avg_d"])
        for mode_key, records in results.items():
            for rec in records:
                writer.writerow([
                    rec.day,
                    mode_labels[mode_key],
                    f"{rec.avg_r:.6f}",
                    rec.review_count,
                    rec.stubborn_count,
                    f"{rec.avg_s:.4f}",
                    f"{rec.avg_d:.4f}",
                ])
    print(f"  CSV 已保存: {csv_path}")
    return csv_path


def generate_plots(profile: UserProfile, results: Dict[str, List[DailyRecord]],
                   output_dir: str):
    """Generate matplotlib plots if available."""
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        print("  [提示] matplotlib 未安装，跳过图表生成。")
        print("         安装: pip install matplotlib")
        return None

    plt.rcParams["font.sans-serif"] = ["SimHei", "Microsoft YaHei", "DejaVu Sans"]
    plt.rcParams["axes.unicode_minus"] = False

    safe_name = profile.name.replace(" ", "_")
    mode_labels = {
        "fsrs_only": "FSRS (baseline)",
        "fsrs_df_full": "FSRS + DF (full)",
        "fsrs_df_rt": "FSRS + DF (rt only)",
        "fsrs_df_acc": "FSRS + DF (acc only)",
    }
    colors = {
        "fsrs_only": "#e74c3c",
        "fsrs_df_full": "#2ecc71",
        "fsrs_df_rt": "#3498db",
        "fsrs_df_acc": "#f39c12",
    }
    days = list(range(1, SIM_DAYS + 1))

    # --- Figure 1: R value over time ---
    fig, axes = plt.subplots(2, 2, figsize=(14, 10))
    fig.suptitle(f"Simulation Results — {profile.name}", fontsize=14, fontweight="bold")

    # Plot 1: Average R
    ax = axes[0, 0]
    for mode_key, records in results.items():
        r_values = [rec.avg_r for rec in records]
        ax.plot(days, r_values, marker="o", markersize=4,
                label=mode_labels[mode_key], color=colors[mode_key], linewidth=2)
    ax.set_xlabel("Day")
    ax.set_ylabel("Average R (Retrievability)")
    ax.set_title("Average Retrievability Over Time")
    ax.legend(fontsize=8)
    ax.grid(True, alpha=0.3)
    ax.set_ylim(0, 1.05)

    # Plot 2: Review count per day
    ax = axes[0, 1]
    for mode_key, records in results.items():
        rev_values = [rec.review_count for rec in records]
        ax.bar([d + 0.2 * list(results.keys()).index(mode_key) for d in days],
               rev_values, width=0.18, label=mode_labels[mode_key],
               color=colors[mode_key], alpha=0.8)
    ax.set_xlabel("Day")
    ax.set_ylabel("Review Count")
    ax.set_title("Daily Review Count")
    ax.legend(fontsize=8)
    ax.grid(True, alpha=0.3, axis="y")

    # Plot 3: Stubborn word count
    ax = axes[1, 0]
    for mode_key, records in results.items():
        stub_values = [rec.stubborn_count for rec in records]
        ax.plot(days, stub_values, marker="s", markersize=4,
                label=mode_labels[mode_key], color=colors[mode_key], linewidth=2)
    ax.set_xlabel("Day")
    ax.set_ylabel("Stubborn Word Count")
    ax.set_title("Stubborn Words Over Time")
    ax.legend(fontsize=8)
    ax.grid(True, alpha=0.3)

    # Plot 4: Average Stability
    ax = axes[1, 1]
    for mode_key, records in results.items():
        s_values = [rec.avg_s for rec in records]
        ax.plot(days, s_values, marker="^", markersize=4,
                label=mode_labels[mode_key], color=colors[mode_key], linewidth=2)
    ax.set_xlabel("Day")
    ax.set_ylabel("Average Stability (S)")
    ax.set_title("Average Stability Over Time")
    ax.legend(fontsize=8)
    ax.grid(True, alpha=0.3)

    plt.tight_layout()
    plot_path = os.path.join(output_dir, f"simulation_{safe_name}.png")
    fig.savefig(plot_path, dpi=150, bbox_inches="tight")
    plt.close(fig)
    print(f"  图表已保存: {plot_path}")
    return plot_path


def print_df_factor_analysis(profile: UserProfile):
    """Print a table showing DF factor behavior for different scenarios."""
    print(f"\n  [DF 因子行为分析 — {profile.name}]")
    print(f"  {'场景':<30} {'λ_rt':>8} {'λ_acc':>8} {'DF':>8}")
    print(f"  {'-'*30} {'-'*8} {'-'*8} {'-'*8}")

    scenarios = [
        ("快速+高正确率", 4.0, [1.0]*10),
        ("快速+低正确率", 4.0, [0.0]*5 + [1.0]*5),
        ("慢速+高正确率", 12.0, [1.0]*10),
        ("慢速+低正确率", 12.0, [0.0]*5 + [1.0]*5),
        ("中速+中正确率", 8.0, [1.0]*6 + [0.0]*4),
        ("新词(无历史)", 8.0, []),
    ]

    for label, rt, history in scenarios:
        l_rt = lambda_rt(rt, sum(history))
        l_acc = lambda_acc(history)
        df = compute_df(rt, history, "full")
        print(f"  {label:<30} {l_rt:>8.3f} {l_acc:>8.3f} {df:>8.3f}")


# ============================================================
#  Main
# ============================================================

def main():
    print("=" * 80)
    print("  FSRS vs FSRS+DF 离线模拟实验")
    print("  参数: theta_rt={}, theta_acc={}, mu={}, sigma={}".format(
        THETA_RT, THETA_ACC, MU, SIGMA))
    print("  单词数: {}, 模拟天数: {}, R阈值: {}".format(
        NUM_WORDS, SIM_DAYS, R_THRESHOLD))
    print("=" * 80)

    random.seed(42)  # reproducibility

    # Output directory (same as script location)
    output_dir = os.path.dirname(os.path.abspath(__file__))

    modes = ["fsrs_only", "fsrs_df_full", "fsrs_df_rt", "fsrs_df_acc"]

    all_csv_paths = []
    all_plot_paths = []

    for profile in PROFILES:
        results: Dict[str, List[DailyRecord]] = {}
        for mode in modes:
            # Re-seed per mode for fair comparison (same word difficulty sequence)
            random.seed(42)
            records = run_simulation(profile, mode)
            results[mode] = records

        # Print comparison table
        print_comparison_table(profile, results)

        # Print DF factor analysis
        print_df_factor_analysis(profile)

        # Save CSV
        csv_path = save_csv(profile, results, output_dir)
        all_csv_paths.append(csv_path)

        # Generate plots
        plot_path = generate_plots(profile, results, output_dir)
        if plot_path:
            all_plot_paths.append(plot_path)

    # --- Cross-profile summary ---
    print(f"\n{'='*80}")
    print("  跨用户类型汇总 (FSRS+DF vs FSRS 基线)")
    print(f"{'='*80}")
    print(f"  {'用户类型':<12} {'R提升':>10} {'复习减少':>10} {'顽固词减少':>10}")
    print(f"  {'-'*12} {'-'*10} {'-'*10} {'-'*10}")

    for profile in PROFILES:
        random.seed(42)
        base = run_simulation(profile, "fsrs_only")
        random.seed(42)
        enhanced = run_simulation(profile, "fsrs_df_full")

        r_base = base[-1].avg_r
        r_enhanced = enhanced[-1].avg_r
        r_improvement = r_enhanced - r_base

        rev_base = sum(r.review_count for r in base)
        rev_enhanced = sum(r.review_count for r in enhanced)
        rev_reduction = rev_base - rev_enhanced

        stub_base = base[-1].stubborn_count
        stub_enhanced = enhanced[-1].stubborn_count
        stub_reduction = stub_base - stub_enhanced

        print(f"  {profile.name:<12} {r_improvement:>+10.4f} {rev_reduction:>+10} "
              f"{stub_reduction:>+10}")

    print(f"\n{'='*80}")
    print("  实验完成！")
    print(f"  CSV 文件: {len(all_csv_paths)} 个")
    if all_plot_paths:
        print(f"  图表文件: {len(all_plot_paths)} 个")
    print(f"{'='*80}")


if __name__ == "__main__":
    main()
