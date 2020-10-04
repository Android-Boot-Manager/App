package org.androidbootmanager.app;

public class ActionAbortedError extends Exception {
    public ActionAbortedError(Exception e) {
        super(e);
    }
}
