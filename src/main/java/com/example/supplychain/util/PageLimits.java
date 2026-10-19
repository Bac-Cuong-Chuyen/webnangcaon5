package com.example.supplychain.util;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;

public final class PageLimits {
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    private PageLimits() {}

    public static PageRequest of(int page, int size) {
        if (page < 0 || size <= 0) {
            throw new IllegalArgumentException("page phai >= 0 va size phai > 0");
        }
        return PageRequest.of(page, Math.min(size, MAX_SIZE), Sort.by("id").ascending());
    }

    public static <T> ResponseEntity<List<T>> response(Slice<T> page) {
        return response(page, page.getContent());
    }

    public static <T, R> ResponseEntity<List<R>> response(Slice<T> page, List<R> rows) {
        return ResponseEntity.ok()
                .header("X-Page", Integer.toString(page.getNumber()))
                .header("X-Page-Size", Integer.toString(page.getSize()))
                .header("X-Has-Next", Boolean.toString(page.hasNext()))
                .body(rows);
    }
}
