package com.emailMarketing.roi;

class HoltWintersAdd {
    private final double alpha,beta,gamma; private final int season; private double level=0, trend=0; private final double[] seasonals; private int count=0;
    HoltWintersAdd(double a,double b,double g,int s){ alpha=a; beta=b; gamma=g; season=s; seasonals=new double[s]; java.util.Arrays.fill(seasonals,0.0);}        
    void update(double value){
        if(count<season){ seasonals[count]=value; level=value; trend=0; count++; return; }
        int idx = count % season; double lastLevel=level; double seasonal=seasonals[idx];
        level = alpha*(value - seasonal) + (1-alpha)*(level + trend);
        trend = beta*(level - lastLevel) + (1-beta)*trend;
        seasonals[idx] = gamma*(value - level) + (1-gamma)*seasonal; count++;
    }
    double forecast(int m){ int idx = (count + m) % season; return level + m*trend + seasonals[idx]; }
}
