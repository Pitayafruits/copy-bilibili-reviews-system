package com.cc.repository;

import com.cc.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {

    // 查询点赞数最高的前10条评论
    List<Comment> findTop10ByOrderByLikesDesc();
}
