package com.archive.center.dto;

import java.time.LocalDate;

/** 到期鉴定台名单中的一行：到期卷的目录信息加未结案鉴定单信息。 */
public record AppraisalPendingRow(
        Long archiveId,
        String code,
        String title,
        Integer archiveYear,
        Integer keepYears,
        Integer expireYear,
        Long roomId,
        String roomName,
        String archiveStatus,
        boolean outOnLoan,
        Long openAppraisalId,
        LocalDate openDate,
        String openAppraiser,
        String openLeader
) {
}
