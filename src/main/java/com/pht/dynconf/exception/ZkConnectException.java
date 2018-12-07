package com.pht.dynconf.exception;

public class ZkConnectException extends RuntimeException {

    private String msg;

    public ZkConnectException(String msg) {
        super(msg);
        this.msg = msg;
    }

    public ZkConnectException(String message, Throwable cause, String msg) {
        super(message, cause);
        this.msg = msg;
    }

    public String getMsg() {
        return msg;
    }
}
