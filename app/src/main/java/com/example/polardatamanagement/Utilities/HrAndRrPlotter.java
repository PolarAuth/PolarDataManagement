package com.example.polardatamanagement.Utilities;

import android.graphics.Color;

import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.XYSeriesFormatter;
import com.polar.sdk.api.model.PolarHrData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import kotlin.jvm.internal.markers.KMutableList;

public class HrAndRrPlotter {

    private final String TAG = "TimePlotter";
    private final int NVALS = 300; // 5 min
    private final double RR_SCALE = .1;

    private PlotterListener listener = null;
    XYSeriesFormatter<?> hrFormatter;
    XYSeriesFormatter<?> rrFormatter;
    SimpleXYSeries hrSeries;
    SimpleXYSeries rrSeries;
    private Double[] xHrVals = new Double[NVALS];
    private Double[] yHrVals = new Double[NVALS];
    private Double[] xRrVals = new Double[NVALS];
    private Double[] yRrVals = new Double[NVALS];

    public HrAndRrPlotter() {
        Date now = new Date();
        double endTime = now.getTime();
        double startTime = endTime - NVALS * 1000;
        double delta = (endTime - startTime) / (NVALS - 1);

        // Specify initial values to keep it from auto sizing
        for (int i=0; i<NVALS; i++) {
            xHrVals[i] = startTime + i * delta;
            yHrVals[i] = 60.0;
            xRrVals[i] = startTime + i * delta;
            yRrVals[i] = 100.0;
        }
        hrFormatter = new LineAndPointFormatter(Color.RED, null, null, null);
        hrFormatter.setLegendIconEnabled(false);
        hrSeries = new SimpleXYSeries(Arrays.asList(xHrVals), Arrays.asList(yHrVals), "HR");
        rrFormatter = new LineAndPointFormatter(Color.BLUE, null, null, null);
        rrFormatter.setLegendIconEnabled(false);
        rrSeries = new SimpleXYSeries(Arrays.asList(xRrVals), Arrays.asList(yRrVals), "RR");
    }

    public XYSeriesFormatter<?> getHrFormatter() {
        return hrFormatter;
    }

    public XYSeriesFormatter<?> getRrFormatter() {
        return rrFormatter;
    }

    public SimpleXYSeries getHrSeries() {
        return hrSeries;
    }

    public SimpleXYSeries getRrSeries() {
        return rrSeries;
    }

    public void addValues(PolarHrData polarHrData) {
        Date now = new Date();
        double time = now.getTime();
        for (int i=0; i<NVALS-1; i++) {
            xHrVals[i] = xHrVals[i + 1];
            yHrVals[i] = yHrVals[i + 1];
            hrSeries.setXY(xHrVals[i], yHrVals[i], i);
        }
        xHrVals[NVALS-1] = time;
        yHrVals[NVALS-1] = (double) polarHrData.hr;
        hrSeries.setXY(xHrVals[NVALS - 1], yHrVals[NVALS - 1], NVALS - 1);

        // Do RR
        // We don't know at what time the RR intervals start.  All we know is
        // the time the data arrived (the current time, now). This
        // implementation assumes they end at the current time, and spaces them
        // out in the past accordingly.  This seems to get the
        // relative positioning reasonably well.

        // Scale the RR values by this to use the same axis. (Could implement
        // NormedXYSeries and use two axes)
        List<Integer> rrsMs = polarHrData.rrsMs;
        int nRrVals = rrsMs.size();
        if (nRrVals > 0) {
            for (int i=0; i<NVALS - nRrVals; i++) {
                xRrVals[i] = xRrVals[i + 1];
                yRrVals[i] = yRrVals[i + 1];
                rrSeries.setXY(xRrVals[i], yRrVals[i], i);
            }
            double totalRR = 0.0;
            for (int i=0; i<nRrVals; i++) {
                totalRR += RR_SCALE * rrsMs.get(i);
            }
            int index = 0;
            Double rr;
            for (int i=NVALS - nRrVals; i<NVALS; i++) {
                rr = RR_SCALE * rrsMs.get(index++);
                xRrVals[i] = time - totalRR;
                yRrVals[i] = rr;
                totalRR -= rr;
                rrSeries.setXY(xRrVals[i], yRrVals[i], i);
            }
        }
        listener.update();
    }

    public void setListener(PlotterListener listener) {
        this.listener = listener;
    }
}
