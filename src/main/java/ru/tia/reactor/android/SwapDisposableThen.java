package ru.tia.reactor.android;

import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.util.annotation.Nullable;

import java.util.function.Consumer;

class SwapDisposableThen implements Disposable.Swap {

    private final Consumer<? super SwapDisposableThen> doOnDispose;
    private final Swap delegate;

    public SwapDisposableThen(Disposable initial, Consumer<? super SwapDisposableThen> doOnDispose){
        this.doOnDispose = doOnDispose;
        delegate = Disposables.swap();
        delegate.replace(initial);
    }

    public SwapDisposableThen(Consumer<? super SwapDisposableThen> doOnDispose){
        this.doOnDispose = doOnDispose;
        delegate = Disposables.swap();
    }

    @Override
    public boolean update(@Nullable Disposable next) {
        return delegate.update(next);
    }

    @Override
    public boolean replace(@Nullable Disposable next) {
        return delegate.update(next);
    }

    @Override
    public Disposable get() {
        return delegate.get();
    }

    @Override
    public void dispose() {
        delegate.dispose();
        doOnDispose.accept(this);
    }

    @Override
    public boolean isDisposed() {
        return delegate.isDisposed();
    }
}
