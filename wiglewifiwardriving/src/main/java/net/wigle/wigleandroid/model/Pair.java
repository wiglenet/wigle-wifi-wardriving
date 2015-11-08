package net.wigle.wigleandroid.model;

public final class Pair<A,B> {
    private A a;
    private B b;

    public Pair( final A a, final B b) {
        this.a = a;
        this.b = b;
    }

    public A getFirst() {
        return a;
    }

    public B getSecond() {
        return b;
    }
}
