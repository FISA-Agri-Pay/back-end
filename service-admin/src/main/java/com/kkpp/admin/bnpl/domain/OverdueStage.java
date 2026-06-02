package com.kkpp.admin.bnpl.domain;

// loan_overdue_ledger.stage — 연체 경과일 기준 단계 구분
// STAGE_1: 1~30일, STAGE_2: 31~60일, STAGE_3: 61일 이상
public enum OverdueStage {
    STAGE_1, STAGE_2, STAGE_3
}
