package com.flowlane.common;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flowlane.common.persistence.Rank;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RankTest {

    @Test
    void betweenAlwaysSortsStrictlyBetween() {
        String a = Rank.first();
        String b = Rank.between(a, null);
        String mid = Rank.between(a, b);
        assertTrue(a.compareTo(mid) < 0 && mid.compareTo(b) < 0);
    }

    @Test
    void repeatedInsertsKeepOrder() {
        List<String> ranks = new ArrayList<>(List.of(Rank.first()));
        Random r = new Random(42);
        for (int i = 0; i < 500; i++) {
            int pos = r.nextInt(ranks.size() + 1);
            String before = pos == 0 ? null : ranks.get(pos - 1);
            String after = pos == ranks.size() ? null : ranks.get(pos);
            ranks.add(pos, Rank.between(before, after));
        }
        List<String> sorted = new ArrayList<>(ranks);
        Collections.sort(sorted);
        assertTrue(sorted.equals(ranks), "ranks must stay in insertion order when sorted");
    }
}
