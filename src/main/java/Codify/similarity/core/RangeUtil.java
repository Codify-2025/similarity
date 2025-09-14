package Codify.similarity.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class RangeUtil {
    private RangeUtil(){}

    public record Interval(int start, int end) {}

    public static List<Interval> mergeRanges(List<Interval> in) {
        if (in==null || in.isEmpty()) return List.of();
        var list = new ArrayList<>(in);
        list.sort(Comparator.comparingInt(Interval::start).thenComparingInt(Interval::end));
        var out = new ArrayList<Interval>();
        Interval cur = list.get(0);
        for (int i=1;i<list.size();i++) {
            var nx = list.get(i);
            if (nx.start() <= cur.end()+1) cur = new Interval(cur.start(), Math.max(cur.end(), nx.end()));
            else { out.add(cur); cur = nx; }
        }
        out.add(cur);
        return out;
    }
}
