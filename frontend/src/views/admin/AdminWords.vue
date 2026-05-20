<template>
  <div class="admin-words fade-in-up">
    <div class="toolbar">
      <el-input v-model="keyword" placeholder="搜索单词" clearable style="width: 200px" @keyup.enter="loadWords" />
      <el-select v-model="examType" placeholder="考试类型" clearable style="width: 120px" @change="loadWords">
        <el-option label="全部" value="" />
        <el-option v-for="t in examTypes" :key="t" :label="t" :value="t" />
      </el-select>
      <el-button type="primary" @click="loadWords">查询</el-button>
      <div style="flex:1"></div>
      <el-button type="danger" plain @click="handleBulkDelete" :disabled="!examType">按类型删除</el-button>
      <el-button type="success" @click="showImportDialog">批量导入</el-button>
      <el-button @click="showAddDialog">新增单词</el-button>
    </div>

    <!-- Word List -->
    <div class="word-list" v-loading="loading">
      <div v-for="word in words" :key="word.id" class="word-card">
        <div class="word-main">
          <span class="word-text">{{ word.word }}</span>
          <span v-if="word.phonetic" class="word-phonetic">{{ word.phonetic }}</span>
        </div>
        <div class="word-meta">
          <el-tag v-if="word.examType" size="small" type="info">{{ word.examType }}</el-tag>
          <span v-if="word.collins" class="meta-item">
            <span class="meta-label">柯林斯</span>
            <span class="meta-value">{{ word.collins }}</span>
          </span>
          <span v-if="word.bncFreq" class="meta-item">
            <span class="meta-label">BNC</span>
            <span class="meta-value">{{ word.bncFreq }}</span>
          </span>
        </div>
        <div class="word-actions">
          <button class="action-chip" @click="showEditDialog(word)">编辑</button>
          <button class="action-chip danger" @click="handleDelete(word)">删除</button>
        </div>
      </div>

      <div v-if="!loading && words.length === 0" class="empty-state">
        <span class="empty-text">暂无单词数据</span>
      </div>
    </div>

    <el-pagination v-model:current-page="page" :page-size="pageSize" :total="total"
      layout="total, prev, pager, next" @current-change="loadWords" style="margin-top: 16px; justify-content: flex-end;" />

    <!-- Add/Edit dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑单词' : '新增单词'" width="480px" :append-to-body="true">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="单词" prop="word"><el-input v-model="form.word" /></el-form-item>
        <el-form-item label="音标"><el-input v-model="form.phonetic" /></el-form-item>
        <el-form-item label="词性"><el-input v-model="form.pos" /></el-form-item>
        <el-form-item label="考试类型">
          <el-select v-model="form.examType" style="width:100%">
            <el-option v-for="t in examTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="柯林斯"><el-input-number v-model="form.collins" :min="0" :max="5" /></el-form-item>
        <el-form-item label="BNC词频"><el-input-number v-model="form.bncFreq" :min="0" /></el-form-item>
        <el-divider />
        <el-form-item v-for="(m, i) in form.meanings" :key="i" :label="'释义 ' + (i + 1)">
          <div style="display:flex; gap:8px; width:100%">
            <el-input v-model="m.partOfSpeech" placeholder="词性" style="width:80px" />
            <el-input v-model="m.meaning" placeholder="含义" style="flex:1" />
            <el-button type="danger" text @click="form.meanings.splice(i, 1)">删除</el-button>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button size="small" @click="form.meanings.push({ partOfSpeech: '', meaning: '' })">+ 添加释义</el-button>
        </el-form-item>
        <el-divider />
        <el-form-item v-for="(e, i) in form.examples" :key="'ex'+i" :label="'例句 ' + (i + 1)">
          <div style="display:flex; flex-direction:column; gap:4px; width:100%">
            <el-input v-model="e.sentence" placeholder="英文例句" />
            <el-input v-model="e.translation" placeholder="中文翻译" />
            <el-button type="danger" text size="small" @click="form.examples.splice(i, 1)" style="align-self:flex-end">删除</el-button>
          </div>
        </el-form-item>
        <el-form-item>
          <el-button size="small" @click="form.examples.push({ sentence: '', translation: '' })">+ 添加例句</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- Import dialog -->
    <el-dialog v-model="importVisible" title="批量导入单词" width="480px" :append-to-body="true">
      <div class="import-hint">
        支持 CSV 和 Excel (.xlsx) 格式<br>
        <span class="import-hint-sub">列顺序：单词、音标、词性、考试类型、柯林斯、BNC词频、FRQ词频</span>
      </div>
      <el-upload drag :auto-upload="false" :limit="1" accept=".csv,.xlsx" :on-change="onFileChange" :file-list="fileList">
        <div style="padding: 24px;">
          <div style="font-size: 28px; color: var(--color-text-muted); margin-bottom: 8px;">&#8682;</div>
          <div style="color: var(--color-text-regular); font-size: 13px;">拖拽文件到此处，或<em style="color: var(--color-primary); font-style: normal; font-weight: 600;">点击上传</em></div>
        </div>
      </el-upload>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" @click="handleImport">开始导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminWords, createAdminWord, updateAdminWord, deleteAdminWord, importWords, deleteWordsByExamType } from '../../api/admin'

const words = ref([])
const loading = ref(false)
const keyword = ref('')
const examType = ref('')
const page = ref(1)
const pageSize = 20
const total = ref(0)
const examTypes = ['高考', 'CET4', 'CET6', '考研']

const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)
const saving = ref(false)
const formRef = ref(null)
const form = ref({ word: '', phonetic: '', pos: '', examType: '', collins: 0, bncFreq: 0, meanings: [], examples: [] })
const rules = {
  word: [{ required: true, message: '请输入单词', trigger: 'blur' }]
}

const importVisible = ref(false)
const importing = ref(false)
const fileList = ref([])
const importFile = ref(null)

onMounted(() => loadWords())

async function loadWords() {
  loading.value = true
  try {
    const res = await getAdminWords({ keyword: keyword.value || undefined, examType: examType.value || undefined, page: page.value - 1, size: pageSize })
    words.value = res.data.content
    total.value = res.data.totalElements
  } catch (e) { console.warn('Words load failed:', e) } finally { loading.value = false }
}

function showAddDialog() {
  isEdit.value = false
  form.value = { word: '', phonetic: '', pos: '', examType: '', collins: 0, bncFreq: 0, meanings: [], examples: [] }
  dialogVisible.value = true
}

function showEditDialog(row) {
  isEdit.value = true
  editId.value = row.id
  form.value = {
    word: row.word, phonetic: row.phonetic, pos: row.pos, examType: row.examType,
    collins: row.collins, bncFreq: row.bncFreq,
    meanings: row.meanings ? row.meanings.map(m => ({ partOfSpeech: m.partOfSpeech || '', meaning: m.meaning || '' })) : [],
    examples: row.examples ? row.examples.map(e => ({ sentence: e.sentence || '', translation: e.translation || '' })) : []
  }
  dialogVisible.value = true
}

async function handleSave() {
  if (!formRef.value) return
  await formRef.value.validate()
  saving.value = true
  try {
    if (isEdit.value) {
      await updateAdminWord(editId.value, form.value)
    } else {
      await createAdminWord(form.value)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    loadWords()
  } catch (e) { ElMessage.error(e.response?.data?.message || '操作失败') }
  finally { saving.value = false }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除单词 "${row.word}" 吗？`, '确认')
    await deleteAdminWord(row.id)
    ElMessage.success('已删除')
    loadWords()
  } catch { /* user cancelled confirm */ }
}

async function handleBulkDelete() {
  if (!examType.value) return
  try {
    await ElMessageBox.confirm(`确定删除所有 "${examType.value}" 类型的单词吗？此操作不可撤销！`, '危险操作', { type: 'error' })
    const res = await deleteWordsByExamType([examType.value])
    ElMessage.success(`已删除 ${res.data.deletedCount} 个单词`)
    loadWords()
  } catch { /* user cancelled */ }
}

function showImportDialog() {
  fileList.value = []
  importFile.value = null
  importVisible.value = true
}

function onFileChange(file) {
  importFile.value = file.raw
}

async function handleImport() {
  if (!importFile.value) { ElMessage.warning('请选择文件'); return }
  importing.value = true
  try {
    const fd = new FormData()
    fd.append('file', importFile.value)
    const res = await importWords(fd)
    ElMessage.success(`导入完成：${res.data.successCount} 成功，${res.data.failCount} 失败`)
    importVisible.value = false
    loadWords()
  } catch (e) { ElMessage.error('导入失败') }
  finally { importing.value = false }
}
</script>

<style scoped>
.admin-words { display: flex; flex-direction: column; gap: 16px; }
.toolbar { display: flex; gap: 10px; flex-wrap: wrap; align-items: center; }

/* === Word List === */
.word-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 200px;
}

.word-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 20px;
  background: #fff;
  border: 2px solid var(--color-border-lighter);
  border-radius: var(--radius-md);
  box-shadow: 0 3px 0 var(--color-border-lighter);
  transition: border-color 0.2s ease, box-shadow 0.2s ease;
}

.word-card:hover {
  border-color: var(--color-primary-lighter);
  box-shadow: 0 3px 0 var(--color-primary-lighter);
}

.word-main {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 180px;
}

.word-text {
  font-size: 15px;
  font-weight: 800;
  color: var(--color-text-primary);
}

.word-phonetic {
  font-size: 13px;
  color: var(--color-text-secondary);
  font-weight: 500;
}

.word-meta {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 14px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.meta-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-muted);
}

.meta-value {
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text-primary);
  font-family: var(--font-mono);
}

.word-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

/* === Action Chips === */
.action-chip {
  padding: 5px 14px;
  border-radius: var(--radius-full);
  border: 1.5px solid var(--color-border-light);
  background: transparent;
  color: var(--color-text-secondary);
  font-family: var(--font-family);
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.action-chip:hover {
  border-color: var(--color-primary);
  color: var(--color-primary-dark);
  background: var(--color-primary-bg);
}

.action-chip:active {
  transform: scale(0.96);
}

.action-chip.danger:hover {
  border-color: var(--color-danger);
  color: var(--color-danger);
  background: var(--color-danger-bg);
}

/* === Empty === */
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
}

.empty-text {
  font-size: 14px;
  color: var(--color-text-muted);
  font-weight: 600;
}

/* === Import === */
.import-hint {
  margin-bottom: 16px;
  font-size: 13px;
  color: var(--color-text-regular);
  line-height: 1.6;
}

.import-hint-sub {
  font-size: 12px;
  color: var(--color-text-secondary);
}

/* === Responsive === */
@media (max-width: 768px) {
  .word-card {
    flex-wrap: wrap;
    gap: 10px;
  }

  .word-main {
    min-width: auto;
  }

  .word-actions {
    width: 100%;
    justify-content: flex-end;
    padding-top: 8px;
    border-top: 1px solid var(--color-border-lighter);
  }
}
</style>
