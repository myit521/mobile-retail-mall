package com.sky.order.internal.application;

import com.sky.entity.IdSegment;

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
