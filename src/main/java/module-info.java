module org.example.podarki {
    requires javafx.controls;
    requires javafx.fxml;


    opens org.example.podarki to javafx.fxml;
    exports org.example.podarki;
}