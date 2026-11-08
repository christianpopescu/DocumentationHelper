package com.documenthelper.cli;

/** Reserved boundary for a future CLI. No command parsing or launcher is implemented. */
public interface CommandLineInterface {
    int execute(String[] arguments);
}
