package com.timo.words.modules.admin.service;

import com.timo.words.common.BusinessException;
import com.timo.words.common.ResultCode;
import com.timo.words.modules.admin.entity.AdminOperationLog;
import com.timo.words.modules.admin.entity.WordImportBatch;
import com.timo.words.modules.admin.repository.AdminOperationLogRepository;
import com.timo.words.modules.admin.repository.WordImportBatchRepository;
import com.timo.words.modules.study.repository.QuizRecordRepository;
import com.timo.words.modules.study.repository.UserWordBindRepository;
import com.timo.words.modules.word.entity.Example;
import com.timo.words.modules.word.entity.Meaning;
import com.timo.words.modules.word.entity.Word;
import com.timo.words.modules.word.repository.WordRepository;
import com.timo.words.modules.user.entity.User;
import com.timo.words.modules.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminWordService {

    private final WordRepository wordRepository;
    private final WordImportBatchRepository batchRepository;
    private final AdminOperationLogRepository operationLogRepository;
    private final QuizRecordRepository quizRecordRepository;
    private final UserWordBindRepository userWordBindRepository;
    private final UserRepository userRepository;

    public Page<Word> listWords(String keyword, String examType, int page, int size) {
        Specification<Word> spec = Specification.where(null);
        if (keyword != null && !keyword.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("word")), "%" + keyword.toLowerCase() + "%"));
        }
        if (examType != null && !examType.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("examType"), examType));
        }
        return wordRepository.findAll(spec, PageRequest.of(page, size, Sort.by("word").ascending()));
    }

    public Word createWord(Word word, Long operatorId, HttpServletRequest request) {
        Word saved = wordRepository.save(word);
        logOperation(operatorId, "WORD_CREATE", "word", saved.getId(), "新增单词: " + word.getWord(), request);
        return saved;
    }

    public Word updateWord(Long id, Word wordData, Long operatorId, HttpServletRequest request) {
        Word word = wordRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.WORD_NOT_FOUND));
        word.setWord(wordData.getWord());
        word.setPhonetic(wordData.getPhonetic());
        word.setPos(wordData.getPos());
        word.setExamType(wordData.getExamType());
        word.setCollins(wordData.getCollins());
        word.setBncFreq(wordData.getBncFreq());
        word.setFrqFreq(wordData.getFrqFreq());
        Word saved = wordRepository.save(word);
        logOperation(operatorId, "WORD_UPDATE", "word", id, "编辑单词: " + word.getWord(), request);
        return saved;
    }

    public void deleteWord(Long id, Long operatorId, HttpServletRequest request) {
        Word word = wordRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.WORD_NOT_FOUND));
        wordRepository.delete(word);
        logOperation(operatorId, "WORD_DELETE", "word", id, "删除单词: " + word.getWord(), request);
    }

    @Transactional
    public int deleteByExamTypes(List<String> examTypes, Long operatorId, HttpServletRequest request) {
        List<Word> words = wordRepository.findByExamTypeIn(examTypes);
        if (words.isEmpty()) return 0;
        List<Long> wordIds = words.stream().map(Word::getId).toList();

        // Delete related records first (foreign key constraints)
        quizRecordRepository.deleteByWordIdIn(wordIds);
        userWordBindRepository.deleteByWordIdIn(wordIds);
        wordRepository.deleteAll(words);

        logOperation(operatorId, "WORD_DELETE", "word", null,
                "批量删除词书: " + String.join("/", examTypes) + " (" + words.size() + "词)", request);
        return words.size();
    }

    @Transactional
    public WordImportBatch importWords(MultipartFile file, Long operatorId, HttpServletRequest request) throws IOException {
        String filename = file.getOriginalFilename();
        WordImportBatch batch = new WordImportBatch();
        batch.setFilename(filename);
        batch.setStatus("PROCESSING");
        batch.setOperatorId(operatorId);
        batch = batchRepository.save(batch);

        List<String[]> rows = new ArrayList<>();
        if (filename != null && filename.endsWith(".xlsx")) {
            rows = parseExcel(file);
        } else {
            rows = parseCsv(file);
        }

        batch.setTotalCount(rows.size());

        // Collect all word texts from import
        Set<String> importWords = rows.stream()
                .filter(r -> r.length > 0 && !r[0].isBlank())
                .map(r -> r[0].trim().toLowerCase())
                .collect(Collectors.toSet());

        // Query existing words to find duplicates
        Set<String> existingWords = wordRepository.findByWordIn(new ArrayList<>(importWords))
                .stream()
                .map(w -> w.getWord().toLowerCase())
                .collect(Collectors.toSet());

        List<Word> toSave = new ArrayList<>();
        int success = 0, fail = 0, duplicate = 0;
        for (String[] row : rows) {
            try {
                if (row.length == 0 || row[0].isBlank()) { fail++; continue; }
                String wordText = row[0].trim();
                if (existingWords.contains(wordText.toLowerCase())) { duplicate++; continue; }

                Word word = new Word();
                word.setWord(wordText);
                if (row.length > 1 && !row[1].isBlank()) word.setPhonetic(row[1]);
                if (row.length > 2 && !row[2].isBlank()) word.setPos(row[2]);
                if (row.length > 3 && !row[3].isBlank()) word.setExamType(row[3]);
                if (row.length > 4 && !row[4].isBlank()) word.setCollins(Integer.parseInt(row[4]));
                if (row.length > 5 && !row[5].isBlank()) word.setBncFreq(Integer.parseInt(row[5]));
                if (row.length > 6 && !row[6].isBlank()) word.setFrqFreq(Integer.parseInt(row[6]));
                toSave.add(word);
                // Add to existing set to prevent duplicates within the same import
                existingWords.add(wordText.toLowerCase());
                success++;
            } catch (Exception e) {
                fail++;
            }
        }

        wordRepository.saveAll(toSave);

        batch.setSuccessCount(success);
        batch.setFailCount(fail + duplicate);
        batch.setStatus("COMPLETED");
        batch = batchRepository.save(batch);

        logOperation(operatorId, "WORD_IMPORT", "word_import_batch", batch.getId(),
                "批量导入: " + filename + " (" + success + "成功/" + duplicate + "重复/" + fail + "失败)", request);

        return batch;
    }

    private List<String[]> parseCsv(MultipartFile file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(reader)) {
            for (CSVRecord record : parser) {
                String[] row = new String[record.size()];
                for (int i = 0; i < record.size(); i++) {
                    row[i] = record.get(i);
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private List<String[]> parseExcel(MultipartFile file) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            boolean first = true;
            for (Row row : sheet) {
                if (first) { first = false; continue; }
                String[] cells = new String[row.getLastCellNum()];
                for (int i = 0; i < row.getLastCellNum(); i++) {
                    Cell cell = row.getCell(i);
                    cells[i] = cell != null ? cell.toString() : "";
                }
                rows.add(cells);
            }
        }
        return rows;
    }

    private void logOperation(Long adminId, String type, String targetType, Long targetId, String detail, HttpServletRequest request) {
        AdminOperationLog log = new AdminOperationLog();
        log.setAdminId(adminId);
        userRepository.findById(adminId).ifPresent(u -> log.setAdminEmail(u.getEmail()));
        log.setOperationType(type);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setDetail(detail);
        log.setIpAddress(request.getRemoteAddr());
        operationLogRepository.save(log);
    }
}
