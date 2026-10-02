package dev.arachneledger;

import java.util.*;

/** Display choices shared by the graph dashboard and graph HUD; never change accounting. */
public final class GraphPreferences {
    public enum Metric {
        PROFIT("Net profit", "Total profit", "Profit / hour", "Projected profit / hour", 0xFF55FF55),
        LOOT("Loot value", "Total loot value", "Total loot / hour", "Projected loot / hour", 0xFFFFAA00),
        COSTS("Costs", "Total costs", "Costs / hour", "Projected costs / hour", 0xFFFF5555);
        private final String label, totalLabel, hourlyLabel, projectedLabel;
        private final int color;
        Metric(String label, String totalLabel, String hourlyLabel, String projectedLabel, int color) {
            this.label=label;this.totalLabel=totalLabel;this.hourlyLabel=hourlyLabel;this.projectedLabel=projectedLabel;this.color=color;
        }
        public String label() { return label; }
        public String totalLabel() { return totalLabel; }
        public String hourlyLabel() { return hourlyLabel; }
        public String projectedLabel() { return projectedLabel; }
        public String projectedTotalLabel() { return this==LOOT?"Projected loot total":this==COSTS?"Projected cost total":"Projected profit total"; }
        public int color() { return color; }
        public int color(double value) { return this==PROFIT&&value<0?0xFFFF5555:color; }
    }
    public Set<Metric> series = EnumSet.of(Metric.PROFIT);
    public Metric primary = Metric.PROFIT;
    public boolean showProjection = false;
    public boolean showSpawns = false;
    public boolean showTotal = true, showHourly = true, showProjectedHourly = true;
    public boolean showProjectedTotal = false;
    public boolean showActiveTime = true, showScope = true, showSpawnCount = false;

    public Set<Metric> enabledSeries() {
        EnumSet<Metric> enabled=EnumSet.noneOf(Metric.class);
        if(series!=null)for(Metric metric:series)if(metric!=null)enabled.add(metric);
        return Collections.unmodifiableSet(enabled);
    }
    public Metric effectiveMetric() {
        var enabled=enabledSeries();
        return enabled.contains(primary)?primary:enabled.stream().findFirst().orElse(Metric.PROFIT);
    }
    public void setVisible(Metric metric, boolean visible) {
        EnumSet<Metric> enabled=EnumSet.noneOf(Metric.class);enabled.addAll(enabledSeries());
        if(visible)enabled.add(metric);else enabled.remove(metric);
        series=enabled;primary=effectiveMetric();
    }
    public void cyclePrimary() {
        var enabled=new ArrayList<>(enabledSeries());
        if(!enabled.isEmpty())primary=enabled.get((enabled.indexOf(effectiveMetric())+1)%enabled.size());
    }
    public void validate() {
        if(series==null)series=EnumSet.of(Metric.PROFIT);
        EnumSet<Metric> enabled=EnumSet.noneOf(Metric.class);enabled.addAll(enabledSeries());series=enabled;
        primary=effectiveMetric();
    }
}
