package Controllers;

import MainClass.SaleSystem;
import db.*;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.stage.Stage;
import javafx.util.Callback;
import javafx.util.StringConverter;
import model.Customer;
import model.Product;
import model.ProductTransaction;
import model.Transaction;
import util.AutoCompleteComboBoxListener;
import util.ButtonCell;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by tjin on 12/7/2015.
 */
public class GenerateProductTransactController {

    private Stage dialogStage;
    private ObservableList<ProductTransaction> productTransactionObservableList;
    private DBExecuteProduct dbExecuteProduct;
    private DBExecuteTransaction dbExecuteTransaction;
    private SaleSystem saleSystem;
    private Transaction transaction;
    private StringBuffer errorMsgBuilder;
    private boolean confirmedClicked;
    private BooleanBinding confimButtonBinding;

    @FXML
    private TableView<ProductTransaction> transactionTableView;
    @FXML
    private TableColumn<ProductTransaction, Integer> productIdCol;
    @FXML
    private TableColumn<ProductTransaction, Integer> stockCol;
    @FXML
    private TableColumn<ProductTransaction, BigDecimal> unitPriceCol;
    @FXML
    private TableColumn<ProductTransaction, Integer> qtyCol;
    @FXML
    private TableColumn<ProductTransaction, BigDecimal> subTotalCol;
//    @FXML
//    private TableColumn deleteCol;

    @FXML
    private Label itemsCountLabel;
    @FXML
    private Label subTotalLabel;
    @FXML
    private Label totalLabel;

    @FXML
    private TextField productIdField;
    @FXML
    private Button addItemButton;

    @FXML
    private Button confirmButton;
    @FXML
    private Button cancelButton;

    @FXML
    private void initialize(){
        confimButtonBinding = transactionTableView.itemsProperty().isNull();
        confirmButton.disableProperty().bind(confimButtonBinding);
        productIdCol.setCellValueFactory(new PropertyValueFactory<>("productId"));
        stockCol.setCellValueFactory(new PropertyValueFactory<>("totalNum"));
        unitPriceCol.setCellValueFactory(new PropertyValueFactory<>("unitPrice"));
        qtyCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));

        loadDataFromDB();

        FilteredList<ProductTransaction> filteredData = new FilteredList<ProductTransaction>(productTransactionObservableList,p->true);
        productIdField.textProperty().addListener((observable,oldVal,newVal)->{
            filteredData.setPredicate(productTransaction -> {
                if (newVal == null || newVal.isEmpty()){
                    return true;
                }
                String lowerCase = newVal.toLowerCase();
                if (String.valueOf(productTransaction.getProductId()).contains(lowerCase)) {
                    return true;
                }
                return false;
            });
            transactionTableView.setItems(filteredData);
        });


        qtyCol.setCellFactory(TextFieldTableCell.forTableColumn(new StringConverter<Integer>(){
            @Override
            public Integer fromString(String string) {
                return Integer.valueOf(string);
            }
            public String toString(Integer integer){
                return String.valueOf(integer);
            }
        }));

        qtyCol.setOnEditCommit(new EventHandler<TableColumn.CellEditEvent<ProductTransaction, Integer>>() {
            @Override
            public void handle(TableColumn.CellEditEvent<ProductTransaction, Integer> event) {
                (event.getTableView().getItems().get(event.getTablePosition().getRow()))
                        .setQuantity(event.getNewValue());

                transactionTableView.setItems(event.getTableView().getItems());
            }
        });
        subTotalCol.setCellValueFactory(new PropertyValueFactory<>("subTotal"));
//        deleteCol.setCellValueFactory(
//                new Callback<TableColumn.CellDataFeatures<ProductTransaction, Boolean>,
//                                        ObservableValue<Boolean>>() {
//                    @Override
//                    public ObservableValue<Boolean> call(TableColumn.CellDataFeatures<ProductTransaction, Boolean> p) {
//                        return new SimpleBooleanProperty(p.getValue() != null);
//                    }
//                });
//
//        deleteCol.setCellFactory(
//                new Callback<TableColumn<ProductTransaction, Boolean>, TableCell<ProductTransaction, Boolean>>() {
//                    @Override
//                    public TableCell<ProductTransaction, Boolean> call(TableColumn<ProductTransaction, Boolean> p) {
//                        return new ButtonCell(transactionTableView);
//                    }
//
//                });
//        productIdField.textProperty().addListener(new ChangeListener<String>() {
//            @Override
//            public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue) {
//                if(productIdField.getText().trim().isEmpty()){
//                    addItemButton.setDisable(true);
//                }
//                else{
//                    addItemButton.setDisable(false);
//                }
//            }
//        });
    }

    @FXML
    public void handleAddItem(){
        List<Product> productResult = dbExecuteProduct.selectFromDatabase(DBQueries.SelectQueries.Product.SELECT_PRODUCTID_PROJECT,
                productIdField.getText().trim());
        if(productResult.isEmpty()){
             Alert alert = new Alert(Alert.AlertType.WARNING);
             alert.initOwner(dialogStage);
             alert.setTitle("Invalid Product ID");
             alert.setHeaderText(null);
             alert.setContentText("Please input valid product ID");
             alert.showAndWait();
         }
        else{
            ProductTransaction newTransaction = new ProductTransaction.ProductTransactionBuilder()
                    .productId(productResult.get(0).getProductId())
                    .totalNum(productResult.get(0).getTotalNum())
                    .unitPrice(productResult.get(0).getUnitPrice())
                    .build();
            productTransactionObservableList.add(newTransaction);
        }

    }


    @FXML
    public void handleCancelButton(){
        this.dialogStage.close();
    }

    @FXML
    public Transaction handleConfirmButton() throws IOException, SQLException {
        transaction.getProductTransactionList().addAll(productTransactionObservableList);
        if(!isTransactionValid()){
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Transaction Is Invalid");
            alert.setHeaderText("Please fix the following errors before proceed");
            alert.setContentText(errorMsgBuilder.toString());
            alert.showAndWait();
            transaction.getProductTransactionList().clear();
        }
        else{
            transaction.setStoreCredit(Double.valueOf(0));

            StringBuffer overviewProductTransactionString = new StringBuffer();
            double total = 0;
            for(ProductTransaction tmp: transaction.getProductTransactionList()){
                overviewProductTransactionString
                        .append("Product ID: " + tmp.getProductId() + " ")
                        .append("Total Num: " + tmp.getTotalNum() + " ")
                        .append("Quantity: " + tmp.getQuantity() + " ")
                        .append("Unit Price: " + tmp.getUnitPrice() + " ")
                        .append("Sub Total: " + tmp.getSubTotal() + " ")
                        .append("\n");
                total+=tmp.getSubTotal();
            }
            transaction.setPayment(-total);


            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Something added here? please confirm", ButtonType.OK, ButtonType.CANCEL);
            alert.setTitle("Transaction Overview");
            alert.setHeaderText("Please confirm the following transaction");
            Optional<ButtonType> result = alert.showAndWait();
            if(result.isPresent() && result.get() == ButtonType.OK){
                commitTransactionToDatabase();
                confirmedClicked = true;
                dialogStage.close();
            }
        }
        return transaction;
    }
    private DBExecuteProductTransaction dbExecute;

    /*
    * Constructor
    * */
    public GenerateProductTransactController(){
        dbExecute = new DBExecuteProductTransaction();
        dbExecuteProduct = new DBExecuteProduct();
        dbExecuteTransaction = new DBExecuteTransaction();
        confirmedClicked = false;
    }

    public void setDialogStage(Stage dialogStage){
        this.dialogStage = dialogStage;
    }





    /*
    * Initilize the main class for this class
    * */
    public void setMainClass(SaleSystem saleSystem){
        this.saleSystem = saleSystem;
        transaction = new Transaction.TransactionBuilder()
                .productInfoList(new ArrayList<ProductTransaction>())
                .staffId(saleSystem.getStaffId())
                .date(new SimpleDateFormat("yyyy-MM-dd").format(new Date()))
                .type(Transaction.TransactionType.IN)
                .storeCredit(0)
                .paymentType("IN")
                .build();
        productTransactionObservableList = FXCollections.observableArrayList(transaction.getProductTransactionList());
        transactionTableView.setItems(productTransactionObservableList);
        productTransactionObservableList.addListener(new ListChangeListener<ProductTransaction>() {
            @Override
            public void onChanged(Change<? extends ProductTransaction> c) {
                while(c.next()){
                    if( c.wasAdded() || c.wasRemoved()){
                        //showPaymentDetails(productTransactionObservableList, customer);
                    }
                }
            }
        });
    }

    private boolean isTransactionValid(){
//        errorMsgBuilder = new StringBuffer();
//        if(!isProductQuantityValid()){
//            errorMsgBuilder.append("Some product's quantity exceeds the stock quota!\n");
//        }
//        if(errorMsgBuilder.length() != 0){
//            return false;
//        }
        return true;
    }


    private boolean isProductQuantityValid(){
        for(ProductTransaction tmp : transaction.getProductTransactionList()){
            if(tmp.getTotalNum() - tmp.getQuantity() < 0){
                return false;
            }
        }
        return true;
    }

    private void commitTransactionToDatabase() throws SQLException, IOException {
        Connection connection = DBConnect.getConnection();
        try{
            connection.setAutoCommit(false);
            //System.out.println(connection.getAutoCommit());
            Object[] objects = ObjectSerializer.TRANSACTION_OBJECT_SERIALIZER.serialize(transaction);
            dbExecuteTransaction.insertIntoDatabase(DBQueries.InsertQueries.Transaction.INSERT_INTO_TRANSACTION,
                    objects);
            for(ProductTransaction tmp : transaction.getProductTransactionList()){
                int remain = tmp.getTotalNum() + tmp.getQuantity();
                dbExecuteProduct.updateDatabase(DBQueries.UpdateQueries.Product.UPDATE_PRODUCT_QUANTITY,
                    remain, tmp.getProductId());
            }
            connection.commit();
        }catch(SQLException e){
            connection.rollback(); //TODO: CRITICAL BUG!!!
            Alert alert = new Alert(Alert.AlertType.ERROR, "Unable to store transaction to database!");
            alert.showAndWait();
        }
        connection.setAutoCommit(true);
    }

    public Transaction returnNewTrasaction(){
        return this.transaction;
    }

    public boolean isConfirmedClicked(){
        return this.confirmedClicked;
    }

    public void loadDataFromDB(){
        productTransactionObservableList = FXCollections.observableArrayList(
                dbExecute.selectFromDatabase(DBQueries.SelectQueries.Product.SELECT_ALL_PRODUCT)
        );
        transactionTableView.setItems(productTransactionObservableList);
    }
}