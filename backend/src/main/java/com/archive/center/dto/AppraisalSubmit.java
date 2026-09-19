package com.archive.center.dto;

/** 会签提交内容：鉴定意见、两个会签人名，续存时还要带续存年数。 */
public class AppraisalSubmit {

    /** 销毁 / 续存 */
    public String opinion;
    public String appraiser;
    public String leader;
    public Integer extendYears;
}
