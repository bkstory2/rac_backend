package com.memo1.memo_server.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/board")
@CrossOrigin(origins = "*")
@Transactional
public class BoardController {

    private final JdbcTemplate jdbc;

    // Board RowMapper
    private final RowMapper<Map<String, Object>> boardRowMapper = new RowMapper<Map<String, Object>>() {
        @Override
        public Map<String, Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
            Map<String, Object> board = new HashMap<>();
            board.put("br_seq", rs.getInt("BR_SEQ"));
            board.put("br_cd", rs.getString("BR_CD"));
            board.put("br_title", rs.getString("BR_TITLE"));
            board.put("br_content", rs.getString("BR_CONTENT"));
            board.put("br_file", rs.getString("BR_FILE"));
            board.put("br_reg_id", rs.getString("BR_REG_ID"));
            board.put("br_reg_dt", rs.getTimestamp("BR_REG_DT"));
            return board;
        }
    };

    // ==================== 게시판 목록 조회 ====================

    /**
     * 게시판 목록 조회
     * 
     * @param brCd 게시판 코드 (A1, A2, A3)
     */
    @GetMapping("/list/{brCd}")
    public List<Map<String, Object>> getBoardList(@PathVariable String brCd) {
        log.info("게시판 목록 조회 - BR_CD: {}", brCd);
        return jdbc.query(
                "SELECT * FROM TBOARD WHERE BR_CD = ? ORDER BY BR_SEQ DESC",
                boardRowMapper,
                brCd);
    }

    /**
     * 게시판 검색 (제목+내용)
     * 
     * @param brCd    게시판 코드
     * @param keyword 검색어
     */
    @GetMapping("/search/{brCd}")
    public List<Map<String, Object>> searchBoard(
            @PathVariable String brCd,
            @RequestParam String keyword) {
        log.info("게시판 검색 - BR_CD: {}, 키워드: {}", brCd, keyword);

        String searchKeyword = "%" + keyword + "%";
        return jdbc.query(
                "SELECT * FROM TBOARD WHERE BR_CD = ? AND (BR_TITLE LIKE ? OR BR_CONTENT LIKE ?) ORDER BY BR_SEQ DESC",
                boardRowMapper,
                brCd, searchKeyword, searchKeyword);
    }

    // ==================== 게시글 상세 조회 ====================

    @GetMapping("/detail/{seq}")
    public ResponseEntity<Map<String, Object>> getBoardDetail(@PathVariable int seq) {
        log.info("게시글 상세 조회 - SEQ: {}", seq);
        try {
            Map<String, Object> board = jdbc.queryForObject(
                    "SELECT * FROM TBOARD WHERE BR_SEQ = ?",
                    boardRowMapper,
                    seq);
            return ResponseEntity.ok(board);
        } catch (Exception e) {
            log.warn("게시글을 찾을 수 없습니다: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    // ==================== 게시글 작성 ====================

    @PostMapping("/write")
    public ResponseEntity<Map<String, Object>> createBoard(@RequestBody Map<String, Object> param) {
        log.info("게시글 작성 요청: {}", param);

        // 필수 파라미터 확인
        String brCd = (String) param.get("br_cd");
        String title = (String) param.get("br_title");
        String content = (String) param.get("br_content");

        if (brCd == null || brCd.trim().isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "게시판 코드(br_cd)는 필수입니다.");
            return ResponseEntity.badRequest().body(error);
        }

        // 선택적 파라미터
        String file = (String) param.getOrDefault("br_file", "");
        String regId = (String) param.getOrDefault("br_reg_id", "admin");

        jdbc.update(
                "INSERT INTO TBOARD (BR_CD, BR_TITLE, BR_CONTENT, BR_FILE, BR_REG_ID) VALUES (?, ?, ?, ?, ?)",
                brCd, title, content, file, regId);

        // 마지막 시퀀스 가져오기
        Integer lastSeq = jdbc.queryForObject("SELECT IDENT_CURRENT('TBOARD')", Integer.class);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("br_seq", lastSeq);
        result.put("message", "게시글이 등록되었습니다.");
        return ResponseEntity.ok(result);
    }

    // ==================== 게시글 수정 ====================

    @PutMapping("/update/{seq}")
    public ResponseEntity<Map<String, Object>> updateBoard(
            @PathVariable int seq,
            @RequestBody Map<String, Object> param) {
        log.info("게시글 수정 - SEQ: {}, 데이터: {}", seq, param);

        String title = (String) param.getOrDefault("br_title", "");
        String content = (String) param.getOrDefault("br_content", "");
        String file = (String) param.getOrDefault("br_file", "");

        int affectedRows = jdbc.update(
                "UPDATE TBOARD SET BR_TITLE = ?, BR_CONTENT = ?, BR_FILE = ? WHERE BR_SEQ = ?",
                title, content, file, seq);

        Map<String, Object> result = new HashMap<>();
        result.put("success", affectedRows > 0);
        result.put("br_seq", seq);
        result.put("message", affectedRows > 0 ? "게시글이 수정되었습니다." : "게시글을 찾을 수 없습니다.");
        return ResponseEntity.ok(result);
    }

    // ==================== 게시글 삭제 ====================

    @DeleteMapping("/delete/{seq}")
    public ResponseEntity<Map<String, Object>> deleteBoard(@PathVariable int seq) {
        log.info("게시글 삭제 - SEQ: {}", seq);

        int affectedRows = jdbc.update(
                "DELETE FROM TBOARD WHERE BR_SEQ = ?",
                seq);

        Map<String, Object> result = new HashMap<>();
        result.put("success", affectedRows > 0);
        result.put("br_seq", seq);
        result.put("message", affectedRows > 0 ? "게시글이 삭제되었습니다." : "게시글을 찾을 수 없습니다.");
        return ResponseEntity.ok(result);
    }

    // ==================== 통합 게시판 API ====================

    /**
     * 모든 게시판 목록 조회 (관리자용)
     */
    @GetMapping("/all")
    public List<Map<String, Object>> getAllBoards() {
        log.info("모든 게시판 목록 조회");
        return jdbc.query(
                "SELECT * FROM TBOARD ORDER BY BR_CD, BR_SEQ DESC",
                boardRowMapper);
    }

    /**
     * 게시판별 통계
     */
    @GetMapping("/stats")
    public Map<String, Object> getBoardStats() {
        log.info("게시판 통계 조회");

        Map<String, Object> stats = new HashMap<>();

        // 각 게시판별 글 수
        List<Map<String, Object>> countByBoard = jdbc.queryForList(
                "SELECT BR_CD, COUNT(*) as count FROM TBOARD GROUP BY BR_CD ORDER BY BR_CD");

        // 총 게시글 수
        Integer totalCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM TBOARD", Integer.class);

        stats.put("count_by_board", countByBoard);
        stats.put("total_count", totalCount);
        stats.put("boards", List.of("A1", "A2", "A3"));

        return stats;
    }
}