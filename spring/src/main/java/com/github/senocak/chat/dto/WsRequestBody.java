package com.github.senocak.chat.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = false)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WsRequestBody {
    String from, to, content, type;
    long date;
}
