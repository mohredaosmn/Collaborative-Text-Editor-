package com.jetbrains.marco.photoz.clone.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Main extends Application {
  @Override
  public void start(Stage primaryStage) throws Exception {
    System.out.println(">>> JavaFX is launching..."); // ADD THIS LINE
  
    Scene scene = new Scene(FXMLLoader.load(getClass().getResource("/fxml/editor.fxml")));
    primaryStage.setTitle("Collaborative Editor");
    primaryStage.setScene(scene);
    primaryStage.show();
  }
  

  public static void main(String[] args) {
    launch(args);
  }
}
