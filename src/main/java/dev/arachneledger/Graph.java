package dev.arachneledger;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import java.util.*;

public final class Graph {
    public static final int GREEN = 0xFF55FF55, RED = 0xFFFF5555, MUTED = 0xFFAAAAAA;
    /** Compatibility overload for callers that only have the original net-profit series. */
    public static void draw(GuiGraphicsExtractor g, Font font, Ledger.Stats stats, int x, int y, int w, int h, int mx, int my, boolean labels) {
        var metric=GraphPreferences.Metric.PROFIT;
        var data=new GraphData.Snapshot(Map.of(metric,stats.graph()),Map.of(),List.of(),
            Map.of(metric,stats.profit()),Map.of(metric,stats.hourly()),Map.of(),Map.of(),
            stats.elapsed(),stats.elapsed(),false,0,0,metric,0);
        draw(g,font,data,new GraphPreferences(),x,y,w,h,mx,my,labels);
    }
    public static void draw(GuiGraphicsExtractor g, Font font, GraphData.Snapshot data, GraphPreferences preferences,
                            int x, int y, int w, int h, int mx, int my, boolean labels) {
        boolean emptyJournal=!data.series().isEmpty()&&data.series().values().stream().allMatch(points->points.size()<=1)&&data.elapsed()==0;
        boolean moneyAxis=labels&&!data.series().isEmpty()&&!emptyJournal;
        int left=x+(moneyAxis?48:2),right=x+w-8,top=y+18,bottom=y+h-(labels?16:3);
        if(right<=left||bottom<=top)return;
        legend(g,font,data,preferences,x+2,y+2,w-4);
        double min=0,max=0;
        for(var points:data.series().values())for(var point:points){min=Math.min(min,point.profit());max=Math.max(max,point.profit());}
        for(var points:data.projections().values())for(var point:points){min=Math.min(min,point.profit());max=Math.max(max,point.profit());}
        if(max-min<1){min=-1;max=1;}
        double pad=(max-min)*.1;min-=pad;max+=pad;
        long duration=Math.max(1,data.duration());
        int divisions=bottom-top<70?2:4;
        for(int i=0;i<=divisions;i++){
            int yy=top+(bottom-top)*i/divisions;
            g.horizontalLine(left,right,yy,0xFF303030);
            if(moneyAxis)g.text(font,Format.coins(max-(max-min)*i/divisions),x+2,yy-3,MUTED,false);
        }
        g.horizontalLine(left,right,yAt(0,min,max,top,bottom),0xFF666666);
        if(preferences.showSpawns){
            Set<Integer> columns=new HashSet<>();
            for(var marker:data.spawns())columns.add(xAt(marker.elapsed(),duration,left,right));
            // verticalLine excludes its endpoints, so short dashes need explicit pixel bounds.
            for(int xx:columns)for(int yy=top;yy<=bottom;yy+=4)g.fill(xx,yy,xx+1,Math.min(yy+2,bottom+1),0xFF5555AA);
        }
        for(var line:data.series().entrySet()){
            var points=line.getValue();int px=left,py=yAt(0,min,max,top,bottom);
            for(int i=0;i<points.size();i++){
                var point=points.get(i);int xx=xAt(point.elapsed(),duration,left,right);
                // Collapse entries in the same pixel while preserving the final step's value.
                while(i+1<points.size()&&xAt(points.get(i+1).elapsed(),duration,left,right)==xx)point=points.get(++i);
                int yy=yAt(point.profit(),min,max,top,bottom),color=color(line.getKey(),point.profit());
                g.horizontalLine(px,xx,py,color);g.verticalLine(xx,Math.min(py,yy),Math.max(py,yy)+1,color);px=xx;py=yy;
            }
            g.horizontalLine(px,xAt(data.elapsed(),duration,left,right),py,color(line.getKey(),data.value(line.getKey())));
        }
        for(var line:data.projections().entrySet()){
            var points=line.getValue();var start=points.getFirst();var end=points.getLast();
            int from=xAt(start.elapsed(),duration,left,right),to=xAt(end.elapsed(),duration,left,right);
            for(int xx=from;xx<=to;xx+=4){
                double value=start.profit()+(end.profit()-start.profit())*(xx-from)/Math.max(1.0,to-from);
                g.horizontalLine(xx,Math.min(xx+1,to),yAt(value,min,max,top,bottom),color(line.getKey(),value));
            }
        }
        if(data.series().isEmpty()&&!preferences.showSpawns)
            g.centeredText(font,font.plainSubstrByWidth("Choose lines in Graph options",right-left-8),(left+right)/2,(top+bottom)/2-12,MUTED);
        else if(emptyJournal)
            g.centeredText(font,"No events recorded yet",(left+right)/2,(top+bottom)/2-12,MUTED);
        if(labels){
            g.text(font,"0:00",left,bottom+5,MUTED,false);
            String time=Format.time(data.duration())+(data.projections().isEmpty()?"":" projected");
            g.text(font,time,right-font.width(time),bottom+5,MUTED,false);
            if(mx>=left&&mx<=right&&my>=top&&my<=bottom){
                long at=(long)((double)(mx-left)/(right-left)*duration);
                StringBuilder text=new StringBuilder("Active time ").append(Format.time(at));
                boolean future=at>data.elapsed()&&!data.projections().isEmpty();
                if(future)text.append(" (projected)");
                for(var line:data.series().entrySet()){
                    double value=valueAt(line.getValue(),at);
                    if(future)value=data.value(line.getKey())+data.projectedHourly(line.getKey())*(at-data.elapsed())/3_600_000.0;
                    text.append('\n').append(line.getKey().label()).append(": ").append(Format.coins(value)).append(" coins");
                }
                if(preferences.showSpawns)for(var marker:data.spawns())if(Math.abs(xAt(marker.elapsed(),duration,left,right)-mx)<=2)
                    text.append("\nArachne spawn · Fight #").append(marker.fightId());
                g.verticalLine(mx,top,bottom,0xFFAAAAAA);
                g.setTooltipForNextFrame(net.minecraft.network.chat.Component.literal(text.toString()),mx,my);
            }
        }
    }
    private static int xAt(long elapsed,long duration,int left,int right){return left+(int)Math.max(0,Math.min(right-left,(double)elapsed/duration*(right-left)));}
    private static int yAt(double value,double min,double max,int top,int bottom){return bottom-(int)((value-min)/(max-min)*(bottom-top));}
    private static int color(GraphPreferences.Metric metric,double value){return metric.color(value);}
    private static double valueAt(List<Ledger.Point> points,long elapsed){
        int low=0,high=points.size()-1;
        while(low<=high){int mid=(low+high)>>>1;if(points.get(mid).elapsed()<=elapsed)low=mid+1;else high=mid-1;}
        return high<0?0:points.get(high).profit();
    }
    private static void legend(GuiGraphicsExtractor g,Font font,GraphData.Snapshot data,GraphPreferences preferences,int x,int y,int width){
        int cursor=x;
        for(var metric:data.series().keySet()){
            String name=switch(metric){case PROFIT->"Profit";case LOOT->"Loot";case COSTS->"Costs";};
            g.text(font,name,cursor,y,metric.color(),false);cursor+=font.width(name)+8;
        }
        if(preferences.showSpawns){g.text(font,"Spawns",cursor,y,0xFF7777FF,false);cursor+=font.width("Spawns")+8;}
        if(!data.projections().isEmpty()){
            int remaining=Math.max(0,x+width-cursor);
            String caption=font.width("+5m dashed")<=remaining?"+5m dashed":"+5m";
            g.text(font,font.plainSubstrByWidth(caption,remaining),cursor,y,MUTED,false);
        }
    }
    private Graph() {}
}
