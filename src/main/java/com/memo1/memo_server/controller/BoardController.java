package com.memo1.memo_server.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
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

    // ==================== 게시판 정보 조회 ====================
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getBoardInfo(@RequestParam("brCd") String brCd) {
        log.info("게시판 정보 조회 - BR_CD: {}", brCd);
        try {
            Map<String, Object> boardInfo = new HashMap<>();
            String brNm = "";
            String description = "";

            switch (brCd) {
                case "B1":
                    brNm = "공지사항";
                    description = "공지사항 게시판입니다.";
                    break;
                case "B2":
                    brNm = "자유게시판";
                    description = "자유롭게 글을 작성할 수 있는 게시판입니다.";
                    break;
                case "B3":
                    brNm = "문의게시판";
                    description = "문의사항을 올리는 게시판입니다.";
                    break;
                default:
                    brNm = "게시판 " + brCd;
                    description = brCd + " 게시판입니다.";
            }

            boardInfo.put("brNm", brNm);
            boardInfo.put("brCd", brCd);
            boardInfo.put("description", description);
            boardInfo.put("totalPosts", getBoardPostCount(brCd));

            return ResponseEntity.ok(boardInfo);
        } catch (Exception e) {
            log.error("게시판 정보 조회 오류: {}", e.getMessage());
            return ResponseEntity.ok(Map.of(
                "brNm", "게시판 " + brCd,
                "brCd", brCd,
                "description", "게시판 " + brCd + "입니다.",
                "totalPosts", 0
            ));
        }
    }

    // ==================== 게시글 목록 조회 ====================
    @GetMapping("/posts")
    public ResponseEntity<Map<String, Object>> getBoardPosts(
            @RequestParam("brCd") String brCd,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        
        log.info("게시글 목록 조회 - BR_CD: {}, page: {}, size: {}", brCd, page, size);

        try {
            if (page < 1) page = 1;
            int offset = (page - 1) * size;
            
            // 총 게시글 수
            int totalCount = getBoardPostCount(brCd);
            int totalPages = (int) Math.ceil((double) totalCount / size);
            
            // 페이지네이션 쿼리
            String sql = "SELECT * FROM TBOARD WHERE BR_CD = ? " +
                        "ORDER BY BR_SEQ DESC " +
                        "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
            
            List<Map<String, Object>> posts = jdbc.query(sql, boardRowMapper, brCd, offset, size);
            
            // 응답 데이터 구성
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("content", posts);
            response.put("totalPages", totalPages);
            response.put("currentPage", page);
            response.put("totalElements", totalCount);
            response.put("size", size);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("게시글 목록 조회 오류: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "success", false,
                        "message", "게시글 목록 조회 실패: " + e.getMessage()
                    ));
        }
    }

    // ==================== 게시글 상세 조회 ====================
    @GetMapping("/detail/{seq}")
    public ResponseEntity<Map<String, Object>> getBoardDetail(@PathVariable("seq") int seq) {
        log.info("게시글 상세 조회 - SEQ: {}", seq);
        try {
            Map<String, Object> board = jdbc.queryForObject(
                    "SELECT * FROM TBOARD WHERE BR_SEQ = ?",
                    boardRowMapper,
                    seq);
            return ResponseEntity.ok(board);
        } catch (Exception e) {
            log.warn("게시글을 찾을 수 없습니다: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "게시글을 찾을 수 없습니다."));
        }
    }

    // ==================== 게시글 작성 ====================
    @PostMapping("/write")
    public ResponseEntity<Map<String, Object>> createBoard(@RequestBody Map<String, Object> param) {
        log.info("게시글 작성 요청: {}", param);
        
        try {
            // 필수 파라미터 확인
            String brCd = (String) param.get("br_cd");
            String title = (String) param.get("br_title");
            String content = (String) param.get("br_content");
            
            if (brCd == null || brCd.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "게시판 코드(br_cd)는 필수입니다."
                ));
            }
            
            // 선택적 파라미터
            String file = (String) param.getOrDefault("br_file", "");
            String regId = (String) param.getOrDefault("br_reg_id", "user");
            
            // 게시글 저장
            jdbc.update(
                "INSERT INTO TBOARD (BR_CD, BR_TITLE, BR_CONTENT, BR_FILE, BR_REG_ID) VALUES (?, ?, ?, ?, ?)",
                brCd, title, content, file, regId
            );
            
            // 생성된 ID 가져오기
            Integer lastSeq = jdbc.queryForObject(
                "SELECT MAX(BR_SEQ) FROM TBOARD WHERE BR_CD = ?", 
                Integer.class, brCd
            );
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "br_seq", lastSeq,
                "message", "게시글이 등록되었습니다."
            ));
            
        } catch (Exception e) {
            log.error("게시글 작성 오류: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "success", false,
                        "message", "게시글 작성 실패: " + e.getMessage()
                    ));
        }
    }

    // ==================== 게시글 수정 ====================
    @PutMapping("/update/{seq}")
    public ResponseEntity<Map<String, Object>> updateBoard(
            @PathVariable("seq") int seq,
            @RequestBody Map<String, Object> param) {
        
        log.info("게시글 수정 - SEQ: {}, 데이터: {}", seq, param);
        
        try {
            String title = (String) param.getOrDefault("br_title", "");
            String content = (String) param.getOrDefault("br_content", "");
            String file = (String) param.getOrDefault("br_file", "");
            
            int affectedRows = jdbc.update(
                "UPDATE TBOARD SET BR_TITLE = ?, BR_CONTENT = ?, BR_FILE = ? WHERE BR_SEQ = ?",
                title, content, file, seq
            );
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", affectedRows > 0);
            result.put("br_seq", seq);
            result.put("message", affectedRows > 0 ? "게시글이 수정되었습니다." : "게시글을 찾을 수 없습니다.");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("게시글 수정 오류: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "success", false,
                        "message", "게시글 수정 실패: " + e.getMessage()
                    ));
        }
    }

    // ==================== 게시글 삭제 ====================
    @DeleteMapping("/delete/{seq}")
    public ResponseEntity<Map<String, Object>> deleteBoard(@PathVariable("seq") int seq) {
        log.info("게시글 삭제 - SEQ: {}", seq);
        
        try {
            int affectedRows = jdbc.update(
                "DELETE FROM TBOARD WHERE BR_SEQ = ?",
                seq
            );
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", affectedRows > 0);
            result.put("br_seq", seq);
            result.put("message", affectedRows > 0 ? "게시글이 삭제되었습니다." : "게시글을 찾을 수 없습니다.");
            
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("게시글 삭제 오류: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "success", false,
                        "message", "게시글 삭제 실패: " + e.getMessage()
                    ));
        }
    }

    // ==================== 게시글 검색 ====================
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchBoard(
            @RequestParam("brCd") String brCd,
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        
        log.info("게시글 검색 - BR_CD: {}, 키워드: {}, page: {}", brCd, keyword, page);
        
        try {
            String searchKeyword = "%" + keyword + "%";
            
            // 총 검색 결과 수
            Integer totalCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM TBOARD WHERE BR_CD = ? AND (BR_TITLE LIKE ? OR BR_CONTENT LIKE ?)",
                Integer.class,
                brCd, searchKeyword, searchKeyword
            );
            
            if (totalCount == null) totalCount = 0;
            int totalPages = (int) Math.ceil((double) totalCount / size);
            int offset = (page - 1) * size;
            
            // 검색 결과 조회
            String sql = "SELECT * FROM TBOARD WHERE BR_CD = ? AND (BR_TITLE LIKE ? OR BR_CONTENT LIKE ?) " +
                        "ORDER BY BR_SEQ DESC " +
                        "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
            
            List<Map<String, Object>> posts = jdbc.query(
                sql, boardRowMapper, 
                brCd, searchKeyword, searchKeyword, offset, size
            );
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("content", posts);
            response.put("totalPages", totalPages);
            response.put("currentPage", page);
            response.put("totalElements", totalCount);
            response.put("size", size);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.error("게시글 검색 오류: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                        "success", false,
                        "message", "검색 실패: " + e.getMessage()
                    ));
        }
    }

    // ==================== 헬퍼 메서드 ====================
    private int getBoardPostCount(String brCd) {
        try {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM TBOARD WHERE BR_CD = ?",
                Integer.class,
                brCd
            );
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("게시글 수 조회 오류: {}", e.getMessage());
            return 0;
        }
    }
}