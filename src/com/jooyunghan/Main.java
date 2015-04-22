package com.jooyunghan;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

class Future<T> {
    static final ExecutorService GLOBAL_EXECUTOR;

    static {
        GLOBAL_EXECUTOR = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    private List<Consumer<T>> completionHandlers = new ArrayList<>();
    private T result = null;
    private boolean completed = false;

    public static <T> Future<T> start(Supplier<T> s) {
        Future<T> t = new Future<T>();
        GLOBAL_EXECUTOR.submit(() -> {
            t.complete(s.get());
        });
        return t;
    }

    public synchronized void onComplete(Consumer<T> c) {
        if (!isCompleted()) {
            completionHandlers.add(c);
        } else {
            c.accept(result);
        }
    }

    public synchronized boolean isCompleted() {
        return completed;
    }

    public synchronized void complete(T result) {
        assert !completed;
        completed = true;
        this.result = result;
        for (Consumer<T> c : completionHandlers) {
            c.accept(result);
        }
    }

    public <R> Future<R> map(Function<T,R> f) {
        Future<R> result = new Future<R>();
        onComplete(t -> result.complete(f.apply(t)));
        return result;
    }

    public <R> Future<R> flatMap(Function<T, Future<R>> f) {
        Future<R> result = new Future<R>();
        onComplete(t -> f.apply(t).onComplete(result::complete));
        return result;
    }
}

public class Main {

    public static void main(String[] args) throws InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        Future<String> fs = Future.start(() -> {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println(Thread.currentThread().getName());
            return "Hello";
        });
        fs.flatMap(s -> Future.start(() -> s + s)).onComplete(s -> {
            System.out.println(Thread.currentThread().getName());
            System.out.println(s);
            countDownLatch.countDown();
        });
        countDownLatch.await();
        Future.GLOBAL_EXECUTOR.shutdown();
    }
}
