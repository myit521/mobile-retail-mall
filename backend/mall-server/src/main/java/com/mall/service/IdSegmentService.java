package com.mall.service;

import com.mall.entity.IdSegment;

public interface IdSegmentService {

    SegmentRange nextSegment(String bizTag);

    class SegmentRange {
        private final long start;
        private final long end;

        public SegmentRange(long start, long end) {
            this.start = start;
            this.end = end;
        }

        public long getStart() {
            return start;
        }

        public long getEnd() {
            return end;
        }
    }
}
