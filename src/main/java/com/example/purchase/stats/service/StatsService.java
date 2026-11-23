package com.example.purchase.stats.service;

import com.example.purchase.stats.domain.MonthlyPurchaseStat;
import com.example.purchase.stats.repository.MonthlyPurchaseStatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;

/**
 * 통계 화면과 대시보드가 읽는 곳. 요청서 테이블을 다시 합산하지 않고 배치가 채운 monthly_purchase_stat만 읽는다.
 * 그래서 화면 숫자는 "마지막 집계 시각" 기준이다 (화면에 같이 보여 준다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StatsService {

    private final MonthlyPurchaseStatRepository statRepository;

    /** 통계 화면: 부서 × 1~12월 표 */
    public YearTable yearTable(int year) {
        List<MonthlyPurchaseStat> stats = statRepository.findBetween(year + "-01", year + "-12");
        Map<Long, DeptRowBuilder> byDept = new LinkedHashMap<>();
        for (MonthlyPurchaseStat s : stats) {
            byDept.computeIfAbsent(s.getDepartment().getId(), id -> new DeptRowBuilder(s.getDepartment().getName()))
                    .put(YearMonth.parse(s.getStatMonth()).getMonthValue(), s);
        }
        List<DeptRow> rows = byDept.values().stream().map(DeptRowBuilder::build).toList();
        List<Cell> totals = new ArrayList<>();
        for (int m = 0; m < 12; m++) {
            int month = m;
            totals.add(new Cell(rows.stream().mapToInt(r -> r.cells().get(month).count()).sum(),
                    rows.stream().map(r -> r.cells().get(month).amount()).reduce(BigDecimal.ZERO, BigDecimal::add)));
        }
        Cell grand = new Cell(rows.stream().mapToInt(r -> r.total().count()).sum(),
                rows.stream().map(r -> r.total().amount()).reduce(BigDecimal.ZERO, BigDecimal::add));
        return new YearTable(year, rows, totals, grand);
    }

    /** 대시보드: 한 부서의 최근 n개월 (가장 오래된 달부터) */
    public List<Bar> departmentTrend(Long departmentId, YearMonth current, int months) {
        YearMonth from = current.minusMonths(months - 1L);
        Map<String, MonthlyPurchaseStat> mine = new HashMap<>();
        statRepository.findBetween(from.toString(), current.toString()).stream()
                .filter(s -> s.getDepartment().getId().equals(departmentId))
                .forEach(s -> mine.put(s.getStatMonth(), s));
        List<Bar> bars = new ArrayList<>();
        for (YearMonth m = from; !m.isAfter(current); m = m.plusMonths(1)) {
            MonthlyPurchaseStat s = mine.get(m.toString());
            bars.add(new Bar(m.getMonthValue() + "월", s == null ? 0 : s.getRequestCount(),
                    s == null ? BigDecimal.ZERO : s.getTotalAmount(), 0));
        }
        return withPercent(bars);
    }

    /** 대시보드: 이번 달 부서별 (금액 큰 순) */
    public List<Bar> byDepartment(YearMonth month) {
        List<Bar> bars = statRepository.findBetween(month.toString(), month.toString()).stream()
                .map(s -> new Bar(s.getDepartment().getName(), s.getRequestCount(), s.getTotalAmount(), 0))
                .sorted(Comparator.comparing(Bar::amount).reversed())
                .toList();
        return withPercent(bars);
    }

    public Optional<LocalDateTime> lastAggregatedAt() {
        return statRepository.lastAggregatedAt();
    }

    /** 막대 길이(%): 가장 큰 값을 100으로. CSS width로 그린다 (차트 라이브러리 없이) */
    private List<Bar> withPercent(List<Bar> bars) {
        BigDecimal max = bars.stream().map(Bar::amount).max(Comparator.naturalOrder()).orElse(BigDecimal.ZERO);
        if (max.signum() == 0) {
            return bars;
        }
        return bars.stream().map(b -> new Bar(b.label(), b.count(), b.amount(),
                b.amount().multiply(BigDecimal.valueOf(100)).divide(max, 0, java.math.RoundingMode.HALF_UP).intValue())).toList();
    }

    public record Cell(int count, BigDecimal amount) {
        static final Cell EMPTY = new Cell(0, BigDecimal.ZERO);
    }

    public record DeptRow(String departmentName, List<Cell> cells, Cell total) {
    }

    public record YearTable(int year, List<DeptRow> rows, List<Cell> monthTotals, Cell grandTotal) {
    }

    public record Bar(String label, int count, BigDecimal amount, int percent) {
    }

    private static final class DeptRowBuilder {
        private final String name;
        private final Cell[] cells = new Cell[12];

        DeptRowBuilder(String name) {
            this.name = name;
            Arrays.fill(cells, Cell.EMPTY);
        }

        void put(int month, MonthlyPurchaseStat s) {
            cells[month - 1] = new Cell(s.getRequestCount(), s.getTotalAmount());
        }

        DeptRow build() {
            List<Cell> list = List.of(cells);
            return new DeptRow(name, list, new Cell(list.stream().mapToInt(Cell::count).sum(),
                    list.stream().map(Cell::amount).reduce(BigDecimal.ZERO, BigDecimal::add)));
        }
    }
}
