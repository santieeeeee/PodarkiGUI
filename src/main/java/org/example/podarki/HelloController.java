package org.example.podarki;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;

public class HelloController {

    @FXML private VBox congratulatorBox;
    @FXML private ListView<Gift> giftListView;
    @FXML private RadioButton concertYes;
    @FXML private RadioButton concertNo;
    @FXML private CheckBox loyalCheckBox;
    @FXML private Label totalLabel;
    @FXML private TextArea orderTextArea;
    @FXML private Button loadButton;

    private final DecimalFormat df = new DecimalFormat("#0.00");
    private final Map<String, LinkedHashMap<String, Double>> giftsByCongratulator = new LinkedHashMap<>();
    private final Map<Gift, Integer> giftQuantities = new HashMap<>();
    private final double CONCERT_PRICE = 5000.0;
    private final List<CheckBox> congratulatorCheckBoxes = new ArrayList<>();

    @FXML
    public void initialize() {
        System.out.println("HelloController.initialize");

        if (loadButton != null) loadButton.setOnAction(e -> onLoadFileClicked());

        if (concertYes != null && concertNo != null) {
            if (concertYes.getToggleGroup() == null && concertNo.getToggleGroup() == null) {
                ToggleGroup tg = new ToggleGroup();
                concertYes.setToggleGroup(tg);
                concertNo.setToggleGroup(tg);
                concertNo.setSelected(true);
            }
            concertYes.setOnAction(e -> onSelectionChanged());
            concertNo.setOnAction(e -> onSelectionChanged());
        }

        if (loyalCheckBox != null) loyalCheckBox.setOnAction(e -> onSelectionChanged());

        if (giftListView != null) {
            giftListView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE); // выбор строки для focus (кол-во через spinner)
            giftListView.setCellFactory(lv -> new ListCell<>() {
                private final HBox root = new HBox(8);
                private final Label nameLabel = new Label();
                private final Label priceLabel = new Label();
                private final Spinner<Integer> qtySpinner = new Spinner<>();

                {
                    HBox.setHgrow(nameLabel, Priority.ALWAYS);
                    qtySpinner.setPrefWidth(80);
                    qtySpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 100, 0));
                    qtySpinner.valueProperty().addListener((obs, oldV, newV) -> {
                        Gift g = getItem();
                        if (g == null) return;
                        if (newV == null || newV <= 0) giftQuantities.remove(g);
                        else giftQuantities.put(g, newV);
                        onSelectionChanged();
                    });
                    root.getChildren().addAll(nameLabel, priceLabel, qtySpinner);
                }

                @Override
                protected void updateItem(Gift g, boolean empty) {
                    super.updateItem(g, empty);
                    if (empty || g == null) {
                        setText(null);
                        setGraphic(null);
                    } else {
                        nameLabel.setText(g.getName());
                        priceLabel.setText(" — " + df.format(g.getPrice()));
                        Integer q = giftQuantities.getOrDefault(g, 0);
                        SpinnerValueFactory<Integer> vf = qtySpinner.getValueFactory();
                        if (vf != null) vf.setValue(q);
                        setGraphic(root);
                    }
                }
            });

            giftListView.getItems().addListener((ListChangeListener<Gift>) c -> {
                Set<Gift> current = new HashSet<>(giftListView.getItems());
                giftQuantities.keySet().removeIf(g -> !current.contains(g));
                onSelectionChanged();
            });
        }

        File defaultFile = new File("podarochki.txt");
        if (defaultFile.exists() && defaultFile.isFile()) {
            boolean ok = loadGiftsFromFile(defaultFile);
            if (ok) buildCongratulatorCheckBoxes();
            updateGiftList();
        }

        recalcAndRender();
    }

    // ----------------- загрузка файла -----------------

    private void onLoadFileClicked() {
        if (loadButton == null) return;
        if (loadButton.getScene() == null || loadButton.getScene().getWindow() == null) {
            javafx.application.Platform.runLater(this::onLoadFileClicked);
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выберите файл с подарками");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Текстовые файлы", "*.txt", "*.csv", "*.*"));
        Window window = loadButton.getScene().getWindow();
        File file = chooser.showOpenDialog(window);
        if (file == null) return;

        boolean ok;
        try {
            ok = loadGiftsFromFile(file);
        } catch (Exception ex) {
            ex.printStackTrace();
            ok = false;
        }

        if (!ok) {
            new Alert(Alert.AlertType.ERROR, "Не удалось загрузить файл. Проверьте формат и кодировку (UTF-8).", ButtonType.OK).showAndWait();
            return;
        }

        buildCongratulatorCheckBoxes();
        updateGiftList();
        recalcAndRender();
    }

    private boolean loadGiftsFromFile(File file) {
        giftsByCongratulator.clear();
        boolean hadValid = false;

        try (BufferedReader br = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            int lineNo = 0;
            while ((line = br.readLine()) != null) {
                lineNo++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split(",", 3);
                if (parts.length < 3) {
                    System.err.println("Skipping malformed line " + lineNo + ": " + line);
                    continue;
                }
                String congratulator = parts[0].trim();
                String gift = parts[1].trim();
                String priceStr = parts[2].trim().replaceAll("\\s+", "");
                double price;
                try {
                    price = Double.parseDouble(priceStr);
                } catch (NumberFormatException nfe) {
                    System.err.println("Bad price at line " + lineNo + ": " + line);
                    continue;
                }
                giftsByCongratulator.computeIfAbsent(congratulator, k -> new LinkedHashMap<>()).put(gift, price);
                hadValid = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        System.out.println("Loaded gifts: " + giftsByCongratulator.size() + " congratulators");
        return hadValid;
    }

    // ----------------- UI построение поздравителей и списка подарков -----------------

    private void buildCongratulatorCheckBoxes() {
        if (congratulatorBox == null) return;
        congratulatorBox.getChildren().clear();
        congratulatorCheckBoxes.clear();
        for (String name : giftsByCongratulator.keySet()) {
            CheckBox cb = new CheckBox(name);
            cb.setOnAction(e -> onSelectionChanged());
            congratulatorCheckBoxes.add(cb);
            congratulatorBox.getChildren().add(cb);
        }
    }

    private void onSelectionChanged() {
        updateGiftList();
        recalcAndRender();
    }

    private void updateGiftList() {
        if (giftListView == null) return;

        List<String> selectedCongratulators = getSelectedCongratulators();
        LinkedHashMap<String, Double> merged = new LinkedHashMap<>();

        if (selectedCongratulators.size() == 1) {
            LinkedHashMap<String, Double> m = giftsByCongratulator.get(selectedCongratulators.get(0));
            if (m != null) merged.putAll(m);
        } else if (selectedCongratulators.isEmpty()) {
            giftListView.setItems(FXCollections.observableArrayList());
            return;
        } else {
            for (String c : selectedCongratulators) {
                LinkedHashMap<String, Double> m = giftsByCongratulator.get(c);
                if (m != null) merged.putAll(m);
            }
        }

        ObservableList<Gift> items = FXCollections.observableArrayList();
        for (Map.Entry<String, Double> e : merged.entrySet()) {
            items.add(new Gift(e.getKey(), e.getValue()));
        }

        Map<String, Integer> oldQuantitiesByName = new HashMap<>();
        for (Map.Entry<Gift, Integer> e : giftQuantities.entrySet()) {
            oldQuantitiesByName.put(e.getKey().getName(), e.getValue());
        }
        giftQuantities.clear();

        for (Gift g : items) {
            Integer q = oldQuantitiesByName.get(g.getName());
            if (q != null && q > 0) giftQuantities.put(g, q);
        }

        giftListView.setItems(items);
    }

    private List<String> getSelectedCongratulators() {
        List<String> res = new ArrayList<>();
        for (CheckBox cb : congratulatorCheckBoxes) {
            if (cb.isSelected()) res.add(cb.getText());
        }
        return res;
    }

    // ----------------- расчёт и вывод -----------------

    private void recalcAndRender() {
        double total = 0.0;
        StringBuilder order = new StringBuilder();

        // Поздравители
        List<String> selectedCongratulators = getSelectedCongratulators();
        if (selectedCongratulators.isEmpty()) {
            order.append("Поздравитель не выбран\n");
        } else {
            order.append("Поздравитель(и): ").append(String.join(", ", selectedCongratulators)).append("\n");
        }

        // Подарки с количеством
        if (!giftQuantities.isEmpty()) {
            order.append("Подарки:\n");
            for (Map.Entry<Gift, Integer> e : giftQuantities.entrySet()) {
                Gift g = e.getKey();
                int qty = e.getValue();
                double line = g.getPrice() * qty;
                total += line;
                order.append("  ").append(g.getName())
                        .append(" x").append(qty)
                        .append(" — ").append(df.format(g.getPrice()))
                        .append(" -> ").append(df.format(line)).append("\n");
            }
        } else {
            order.append("Подарок(и) не выбраны\n");
        }

        // Концерт
        boolean concert = concertYes != null && concertYes.isSelected();
        if (concert) {
            total += CONCERT_PRICE;
            order.append("Концерт: Да — ").append(df.format(CONCERT_PRICE)).append("\n");
        } else {
            order.append("Концерт: Нет\n");
        }

        // Скидка постоянного клиента
        boolean loyal = loyalCheckBox != null && loyalCheckBox.isSelected();
        if (loyal) {
            double before = total;
            double discount = total * 0.10;
            total -= discount;
            order.append("Постоянный клиент: Да — скидка 10% (").append(df.format(discount)).append(")\n");
            order.append("Итого до скидки: ").append(df.format(before)).append("\n");
        } else {
            order.append("Постоянный клиент: Нет\n");
        }

        if (totalLabel != null) totalLabel.setText(df.format(total));
        order.append("Итог: ").append(df.format(total)).append("\n");
        if (orderTextArea != null) orderTextArea.setText(order.toString());
    }
}
