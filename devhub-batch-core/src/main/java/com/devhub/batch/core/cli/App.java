package com.devhub.batch.core.cli;

import com.devhub.batch.core.config.BatchConfig;

/** Main class — wires config loading to the CLI dispatcher. */
public final class App {

    private App() {}

    public static void main(String[] args) {
        int exitCode = new Cli(BatchConfig.load()).dispatch(args);
        System.exit(exitCode);
    }
}
