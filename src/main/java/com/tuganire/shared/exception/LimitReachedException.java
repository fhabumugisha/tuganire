package com.tuganire.shared.exception;

import lombok.Getter;

@Getter
public class LimitReachedException extends BusinessException {

    public enum LimitType {
        SERMONS, SEARCHES
    }

    private final LimitType limitType;
    private final int limit;
    private final int current;

    public LimitReachedException(LimitType limitType, int limit, int current) {
        super(limitType == LimitType.SERMONS ? "sermon.limit.reached" : "search.limit.reached");
        this.limitType = limitType;
        this.limit = limit;
        this.current = current;
    }

    public int getRemaining() {
        return Math.max(0, limit - current);
    }
}
