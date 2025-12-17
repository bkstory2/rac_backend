package com.memo1.memo_server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j; // 추가

@Slf4j // 추가
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/memos")
@CrossOrigin(origins = "*")
@Transactional // ← 추가!
public class MemoController {

    private final JdbcTemplate jdbc;

    @GetMapping
    public List<Map<String, Object>> list() {
        log.info("메모 조회 요청 수신 22222");

        List<Map<String, Object>> result = jdbc.queryForList(
                "SELECT FID, FTITLE, FCONTENT, FCREATED_AT FROM MEMO ORDER BY FID DESC");

        // 실제 반환되는 데이터 구조 확인
        if (!result.isEmpty()) {
            log.info("첫 번째 메모 데이터: {}", result.get(0));
            log.info("키셋: {}", result.get(0).keySet());
        }

        return result;
    }

    // MemoController.java - 수정 부분
    @PostMapping
    public ResponseEntity<Map<String, Object>> save(@RequestBody Map<String, Object> param) {
        log.info("저장 요청 수신: {}", param);

        // fid 파라미터 처리 수정
        Object fidObj = param.get("fid");
        Integer fid = null;

        if (fidObj != null) {
            try {
                if (fidObj instanceof Number) {
                    fid = ((Number) fidObj).intValue();
                } else if (fidObj instanceof String) {
                    String fidStr = (String) fidObj;
                    if (!fidStr.trim().isEmpty()) {
                        fid = Integer.parseInt(fidStr);
                    }
                }
            } catch (Exception e) {
                log.warn("fid 파싱 오류: {}, 값: {}", e.getMessage(), fidObj);
            }
        }

        if (fid == null || fid <= 0) {
            // INSERT
            log.info("새 메모 추가");
            jdbc.update(
                    "INSERT INTO MEMO (FTITLE, FCONTENT) VALUES (?, ?)",
                    param.get("ftitle"),
                    param.get("fcontent"));

            // 마지막 삽입 ID 가져오기
            Integer lastId = jdbc.queryForObject(
                    "SELECT IDENT_CURRENT('MEMO')", Integer.class);

            log.info("새 메모 ID: {}", lastId);

            Map<String, Object> result = new HashMap<>();
            result.put("fid", lastId);
            result.put("success", true);
            result.put("message", "메모가 추가되었습니다.");
            return ResponseEntity.ok(result);
        } else {
            // UPDATE
            log.info("메모 수정 - ID: {}", fid);
            int affectedRows = jdbc.update(
                    "UPDATE MEMO SET FTITLE = ?, FCONTENT = ? WHERE FID = ?",
                    param.get("ftitle"),
                    param.get("fcontent"),
                    fid);

            log.info("수정된 행 수: {}", affectedRows);

            Map<String, Object> result = new HashMap<>();
            result.put("fid", fid);
            result.put("success", affectedRows > 0);
            result.put("message", affectedRows > 0 ? "메모가 수정되었습니다." : "수정할 메모를 찾을 수 없습니다.");
            return ResponseEntity.ok(result);
        }
    }

    @DeleteMapping("/{fid}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable int fid) {
        jdbc.update("DELETE FROM MEMO WHERE FID = ?", fid);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("fid", fid);
        return ResponseEntity.ok(result);
    }
}