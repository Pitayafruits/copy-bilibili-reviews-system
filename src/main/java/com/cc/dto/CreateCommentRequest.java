package com.cc.dto;

import lombok.Data;

@Data
public class CreateCommentRequest {

    private String content;
    private String userId;
}
