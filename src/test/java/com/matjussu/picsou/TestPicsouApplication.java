package com.matjussu.picsou;

import org.springframework.boot.SpringApplication;

public class TestPicsouApplication {

  public static void main(String[] args) {
    SpringApplication.from(PicsouApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
