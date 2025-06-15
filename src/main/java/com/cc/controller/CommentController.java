package com.cc.controller;

import com.cc.dto.CreateCommentRequest;
import com.cc.entity.Comment;
import com.cc.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/comments")
public class CommentController {

    @Autowired
    private CommentService commentService;

    // 发布评论
    @PostMapping
    public ResponseEntity<Comment> createComment(@RequestBody CreateCommentRequest request) {
        Comment comment = commentService.createComment(request.getContent(), request.getUserId());
        return new ResponseEntity<>(comment, HttpStatus.CREATED);
    }

    // 点赞评论
    @PostMapping("/{id}/like")
    public ResponseEntity<Comment> likeComment(@PathVariable Long id) {
        try {
            Comment comment = commentService.likeComment(id);
            return ResponseEntity.ok(comment);
        } catch (Exception e) {
            // 这里可以更精细地处理乐观锁冲突异常
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    // 获取点赞最多的10条评论
    @GetMapping("/hot")
    public ResponseEntity<List<Comment>> getHotComments() {
        List<Comment> comments = commentService.getTop10HotComments();
        return ResponseEntity.ok(comments);
    }

}
