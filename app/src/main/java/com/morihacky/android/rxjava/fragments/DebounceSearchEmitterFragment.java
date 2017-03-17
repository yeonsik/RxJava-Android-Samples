package com.morihacky.android.rxjava.fragments;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;

import com.jakewharton.rxbinding.widget.RxTextView;
import com.jakewharton.rxbinding.widget.TextViewTextChangeEvent;
import com.morihacky.android.rxjava.R;
import com.morihacky.android.rxjava.retrofit.GithubApi;
import com.morihacky.android.rxjava.retrofit.GithubService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import hu.akarnokd.rxjava.interop.RxJavaInterop;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.observers.DisposableObserver;
import timber.log.Timber;

import static co.kaush.core.util.CoreNullnessUtils.isNotNullOrEmpty;
import static java.lang.String.format;

public class DebounceSearchEmitterFragment
      extends BaseFragment {

    @Bind(R.id.list_threading_log) ListView _logsList;
    @Bind(R.id.input_txt_debounce) EditText _inputSearchText;

    private LogAdapter _adapter;
    private List<String> _logs;

    private Disposable _disposable;

    private GithubApi _githubService;

    @Override
    public void onDestroy() {
        super.onDestroy();
        _disposable.dispose();
        ButterKnife.unbind(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View layout = inflater.inflate(R.layout.fragment_debounce, container, false);
        ButterKnife.bind(this, layout);
        return layout;
    }

    @OnClick(R.id.clr_debounce)
    public void onClearLog() {
        _logs = new ArrayList<>();
        _adapter.clear();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {

        super.onActivityCreated(savedInstanceState);
        _setupLogger();

        String githubToken = getResources().getString(R.string.github_oauth_token);
        _githubService = GithubService.createGithubService(githubToken);

//        _inputSearchText.setText("retrofit");

        _disposable = RxJavaInterop.toV2Observable(RxTextView.textChangeEvents(_inputSearchText))
                // http://reactivex.io/documentation/operators/debounce.html
              .debounce(400, TimeUnit.MILLISECONDS)// default Scheduler is Computation

                // 일반 방식
                .filter(changes -> isNotNullOrEmpty(changes.text().toString()))
                // 렉시컬 스코프 방식을 사용한 코드 중복 처리
                .filter(isNotEmpty())
                .filter(isNotEmpty)
              .observeOn(AndroidSchedulers.mainThread())
              .subscribeWith(_getSearchObserver());
    }

    // -----------------------------------------------------------------------------------
    // Main Rx entities

    private DisposableObserver<TextViewTextChangeEvent> _getSearchObserver() {
        return new DisposableObserver<TextViewTextChangeEvent>() {
            @Override
            public void onComplete() {
                Timber.d("--------- onComplete");
            }

            @Override
            public void onError(Throwable e) {
                Timber.e(e, "--------- Woops on error!");
                _log("Dang error. check your logs");
            }

            @Override
            public void onNext(TextViewTextChangeEvent onTextChangeEvent) {
                _log(format("Searching for %s", onTextChangeEvent.text().toString()));
            }
        };
    }

    // -----------------------------------------------------------------------------------
    // Method that help wiring up the example (irrelevant to RxJava)

    private void _setupLogger() {
        _logs = new ArrayList<>();
        _adapter = new LogAdapter(getActivity(), new ArrayList<>());
        _logsList.setAdapter(_adapter);
    }

    private void _log(String logMsg) {

        if (_isCurrentlyOnMainThread()) {
            _logs.add(0, logMsg + " (main thread) ");
            _adapter.clear();
            _adapter.addAll(_logs);
        } else {
            _logs.add(0, logMsg + " (NOT main thread) ");

            // You can only do below stuff on main thread.
            new Handler(Looper.getMainLooper()).post(() -> {
                _adapter.clear();
                _adapter.addAll(_logs);
            });
        }
    }

    private boolean _isCurrentlyOnMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    private class LogAdapter
          extends ArrayAdapter<String> {

        public LogAdapter(Context context, List<String> logs) {
            super(context, R.layout.item_log, R.id.item_log, logs);
        }
    }

    // -----------------------------------------------------------------------------------
    // 람다 표현식의 코드 중복 처리하기

    private Predicate<TextViewTextChangeEvent> isNotEmpty() {
        return changes -> isNotNullOrEmpty(changes.text().toString());
    }

    final Predicate<TextViewTextChangeEvent> isNotEmpty =
            changes -> isNotNullOrEmpty(changes.text().toString());

//    .observeOn(Schedulers.io())
//            .flatMap(changeEvent -> {
//        final String text = changeEvent.view().getText().toString();
//        return _githubService.contributors("square", text)
//                .onErrorResumeNext(Observable.empty());
//    })
}