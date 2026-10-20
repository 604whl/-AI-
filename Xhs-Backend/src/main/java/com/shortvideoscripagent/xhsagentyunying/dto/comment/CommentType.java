package com.shortvideoscripagent.xhsagentyunying.dto.comment;

/**
 * 评论生成场景枚举。
 */
public enum CommentType {

    /** 首问回复：用户在笔记下主动提问时的回复话术 */
    FIRST_QUESTION,

    /** 追问：已回复过一轮后的跟进提问 */
    ASK_FOLLOW_UP,

    /** 私信话术：引导用户私信获取更多信息 */
    PRIVATE_MESSAGE,

    /** 转化话术：软性转化，引导咨询/留资 */
    CONVERSION
}