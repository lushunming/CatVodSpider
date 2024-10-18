package com.github.catvod.bean.uc;

public  class LCSResult {
        public int length;
        public String sequence;
        public int offset;

        public LCSResult(int length, String sequence, int offset) {
            this.length = length;
            this.sequence = sequence;
            this.offset = offset;
        }
    }