package ru.algotraide.object;

public class PairTriangle {

    private String firstPair;
    private String secondPair;
    private String thirdPair;
    private boolean direct;

    public PairTriangle(String firstPair, String secondPair, String thirdPair, boolean direct) {
        this.firstPair = firstPair;
        this.secondPair = secondPair;
        this.thirdPair = thirdPair;
        this.direct = direct;
    }

    public String getFirstPair() {
        return firstPair;
    }

    public void setFirstPair(String firstPair) {
        this.firstPair = firstPair;
    }

    public String getSecondPair() {
        return secondPair;
    }

    public void setSecondPair(String secondPair) {
        this.secondPair = secondPair;
    }

    public String getThirdPair() {
        return thirdPair;
    }

    public void setThirdPair(String thirdPair) {
        this.thirdPair = thirdPair;
    }

    public boolean isDirect() {
        return direct;
    }

    public void setDirect(boolean direct) {
        this.direct = direct;
    }

    @Override
    public String toString() {
        return "PairTriangle{" + firstPair + ' ' + secondPair + ' ' + thirdPair + '}';
    }
}
