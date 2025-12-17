package com.memo1.memo_server.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/memos")
@Transactional
public class MemoController {

    private final JdbcTemplate jdbc;

    // ==================== 모든 메모 조회 ====================
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllMemos() {
        log.info("메모 전체 조회 요청");
        
        try {
            List<Map<String, Object>> result = jdbc.queryForList(
                "SELECT FID, FTITLE, FCONTENT, FCREATED_AT FROM MEMO ORDER BY FID DESC"
            );
            
            log.info("조회된 메모 개수: {}", result.size());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("content", result);
            response.put("totalElements", result.size());
            response.put("message", "메모 조회 성공");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("메모 조회 오류: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "success", false,
                        "message", "메모 조회 실패: " + e.getMessage()
                    ));
        }
    }

    // ==================== 특정 메모 조회 ====================
    @GetMapping("/{fid}")
    public ResponseEntity<Map<String, Object>> getMemoById(@PathVariable("fid") int fid) {
        log.info("메모 상세 조회 - FID: {}", fid);
        
        try {
            Map<String, Object> memo = jdbc.queryForMap(
                "SELECT FID, FTITLE, FCONTENT, FCREATED_AT FROM MEMO WHERE FID = ?",
                fid
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("content", memo);
            response.put("message", "메모 조회 성공");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.warn("메모를 찾을 수 없습니다. FID: {}, 오류: {}", fid, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                        "success", false,
                        "message", "메모를 찾을 수 없습니다. FID: " + fid
                    ));
        }
    }

    // ==================== 메모 저장/수정 ====================
    @PostMapping
    public ResponseEntity<Map<String, Object>> saveMemo(@RequestBody Map<String, Object> param) {
        log.info("메모 저장/수정 요청: {}", param);

        try {
            // 필수 파라미터 검증
            String ftitle = (String) param.get("ftitle");
            String fcontent = (String) param.get("fcontent");
            
            if ((ftitle == null || ftitle.trim().isEmpty()) && 
                (fcontent == null || fcontent.trim().isEmpty())) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "제목이나 내용 중 하나는 입력해야 합니다."
                ));
            }
            
            // fid 파라미터 처리
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

        Map<String, Object> result = new HashMap<>();
            
        if (fid == null || fid <= 0) {
            // INSERT - 새로운 메모 추가
            log.info("새 메모 추가");
            jdbc.update(
                "INSERT INTO MEMO (FTITLE, FCONTENT) VALUES (?, ?)",
                ftitle != null ? ftitle : "",
                fcontent != null ? fcontent : ""
            );

            // 마지막 삽입 ID 가져오기
            Integer lastId = jdbc.queryForObject(
                "SELECT IDENT_CURRENT('MEMO')", Integer.class
            );

            log.info("새 메모 ID: {}", lastId);

            result.put("fid", lastId);
            result.put("success", true);
            result.put("message", "메모가 추가되었습니다.");
            result.put("action", "insert");
            
        } else {
            // UPDATE - 기존 메모 수정
            log.info("메모 수정 - ID: {}", fid);
            int affectedRows = jdbc.update(
                "UPDATE MEMO SET FTITLE = ?, FCONTENT = ? WHERE FID = ?",
                ftitle != null ? ftitle : "",
                fcontent != null ? fcontent : "",
                fid
            );

            log.info("수정된 행 수: {}", affectedRows);

            result.put("fid", fid);
            result.put("success", affectedRows > 0);
            result.put("action", "update");
            result.put("message", affectedRows > 0 ? 
                "메모가 수정되었습니다." : "수정할 메모를 찾을 수 없습니다.");
        }
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("메모 저장/수정 오류: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "success", false,
                        "message", "메모 저장/수정 실패: " + e.getMessage()
                    ));
        }
    }

    // ==================== 메모 삭제 ====================
    @DeleteMapping("/{fid}")
    public ResponseEntity<Map<String, Object>> deleteMemo(@PathVariable("fid") int fid) {
        log.info("메모 삭제 요청 - FID: {}", fid);
        
        try {
            int affectedRows = jdbc.update(
                "DELETE FROM MEMO WHERE FID = ?", 
                fid
            );

            Map<String, Object> result = new HashMap<>();
            result.put("success", affectedRows > 0);
            result.put("fid", fid);
            result.put("message", affectedRows > 0 ? 
                "메모가 삭제되었습니다." : "삭제할 메모를 찾을 수 없습니다.");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("메모 삭제 오류: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "success", false,
                        "message", "메모 삭제 실패: " + e.getMessage()
                    ));
        }
    }

    // ==================== 메모 검색 ====================
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchMemos(
            @RequestParam("keyword") String keyword) {
        
        log.info("메모 검색 요청 - 키워드: {}", keyword);
        
        try {
            String searchKeyword = "%" + keyword + "%";
            
            List<Map<String, Object>> result = jdbc.queryForList(
                "SELECT FID, FTITLE, FCONTENT, FCREATED_AT " +
                "FROM MEMO " +
                "WHERE FTITLE LIKE ? OR FCONTENT LIKE ? " +
                "ORDER BY FID DESC",
                searchKeyword, searchKeyword
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("content", result);
            response.put("totalElements", result.size());
            response.put("message", "검색 완료");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("메모 검색 오류: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "success", false,
                        "message", "메모 검색 실패: " + e.getMessage()
                    ));
        }
    }

    // ==================== 메모 통계 ====================
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getMemoStats() {
        log.info("메모 통계 요청");
        
        try {
            // 총 메모 수
            Integer totalCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM MEMO", 
                Integer.class
            );
            
            // 제목이 있는 메모 수
            Integer titledCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM MEMO WHERE FTITLE IS NOT NULL AND FTITLE != ''", 
                Integer.class
            );
            
            // 내용이 있는 메모 수
            Integer contentCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM MEMO WHERE FCONTENT IS NOT NULL AND FCONTENT != ''", 
                Integer.class
            );
            
            // 최근 생성된 메모
            List<Map<String, Object>> recentMemos = jdbc.queryForList(
                "SELECT TOP 5 FID, FTITLE, FCREATED_AT " +
                "FROM MEMO " +
                "ORDER BY FCREATED_AT DESC"
            );

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("totalMemos", totalCount != null ? totalCount : 0);
            response.put("titledMemos", titledCount != null ? titledCount : 0);
            response.put("contentMemos", contentCount != null ? contentCount : 0);
            response.put("recentMemos", recentMemos);
            response.put("message", "통계 조회 성공");
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("메모 통계 조회 오류: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "success", false,
                        "message", "통계 조회 실패: " + e.getMessage()
                    ));
        }
    }
}