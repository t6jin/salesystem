package Controllers;

import db.DBExecuteCustomer;
import db.DBQueries;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Stage;
import model.Customer;
import MainClass.SaleSystem;
import org.apache.log4j.Logger;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Created by tjin on 11/29/2015.
 */
public class CustomerOverviewController implements OverviewController{

    private static Logger logger= Logger.getLogger(CustomerOverviewController.class);
    private DBExecuteCustomer dbExecute;
    private ObservableList<Customer> customerList;
    private SaleSystem saleSystem;
    private Executor executor;
    private Stage dialogStage;

    @FXML
    private TableView<Customer> customerTable;
    @FXML
    private TextField filterField;
    @FXML
    private TableColumn<Customer, String> firstNameCol;
    @FXML
    private TableColumn<Customer, String> lastNameCol;
    @FXML
    private TableColumn<Customer, String> phoneCol;
    @FXML
    private Label firstNameLabel;
    @FXML
    private Label lastNameLabel;
    @FXML
    private Label streetLabel;
    @FXML
    private Label postalCodeLabel;
    @FXML
    private Label cityLabel;
    @FXML
    private Label phoneLabel;
    @FXML
    private Label classLabel;
    @FXML
    private Label emailLabel;
    @FXML
    private Label storeCreditLabel;
    @FXML
    private Label companyLabel;
    @FXML
    private Label pstNumberLabel;
    @FXML
    private ProgressBar progressBar;

    @FXML
    private void initialize(){
        firstNameCol.setCellValueFactory(new PropertyValueFactory<Customer, String>("firstName"));
        lastNameCol.setCellValueFactory(new PropertyValueFactory<Customer, String>("lastName"));
        phoneCol.setCellValueFactory(new PropertyValueFactory<Customer, String>("phone"));
        customerTable.getSelectionModel().selectedItemProperty().addListener(
            new ChangeListener<Customer>() {
                @Override
                public void changed(ObservableValue<? extends Customer> observable, Customer oldValue, Customer newValue) {
                    showCustomerDetail(newValue);
                }
            }
        );
        executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return  t;
        });
    }

    @FXML
    private void handleDeleteCustomer(){
        int selectedIndex = customerTable.getSelectionModel().getSelectedIndex();
        if(selectedIndex >= 0){
            String tempFirstName = customerTable.getItems().get(selectedIndex).getFirstName();
            String tempLastName = customerTable.getItems().get(selectedIndex).getLastName();
            Alert alertConfirm = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to delete this customer?");
            Optional<ButtonType> result =  alertConfirm.showAndWait();
            boolean flag = true;
            if(result.isPresent() && result.get() == ButtonType.OK){
                try {
                    dbExecute.deleteDatabase(DBQueries.DeleteQueries.Customer.DELETE_FROM_CUSTOMER,
                            customerTable.getItems().get(selectedIndex).getUserName());
                } catch (SQLException e) {
                    logger.error(e.getMessage());
                    flag = false;
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Delete Customer Error");
                    alert.setHeaderText(null);
                    alert.setContentText("Error when deleting customer "+tempFirstName+" "+tempLastName);
                    alert.showAndWait();
                }finally {
                    if(flag){
                        customerTable.getItems().remove(selectedIndex);
                    }
                }
            }
        }
        else{
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Customer Selected");
            alert.setHeaderText(null);
            alert.setContentText("Please select a person in the table.");
            alert.showAndWait();
        }

    }

    @FXML
     private void handleAddCustomer(){
        Customer newCustomer = new Customer(new Customer.CustomerBuilder());
        boolean okClicked = saleSystem.showCustomerEditDialog(newCustomer);
        if(okClicked){
            newCustomer.setUserName();
            try{
                dbExecute.insertIntoDatabase(DBQueries.InsertQueries.Customer.INSERT_INTO_CUSTOMER,
                        newCustomer.getAllProperties());
            }catch(SQLException e){
                logger.error(e.getMessage());
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Unable To Add New Customer");
                alert.setHeaderText(null);
                alert.setContentText("Unable To Add New Customer" + newCustomer.getFirstName() + " " + newCustomer.getLastName());
                alert.showAndWait();
            }finally {
                loadDataFromDB();
            }
        }
    }

    @FXML
    private void handleEditCustomer(){
        Customer selectedCustomer = customerTable.getSelectionModel().getSelectedItem();
        if(selectedCustomer != null){
            boolean onClicked = saleSystem.showCustomerEditDialog(selectedCustomer);
            if(onClicked){
                try{
                    dbExecute.updateDatabase(DBQueries.UpdateQueries.Customer.UPDATE_CUSTOMER,
                            selectedCustomer.getAllPropertiesForUpdate());
                }catch(SQLException e){
                    logger.error(e.getMessage());
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Unable To Edit New Customer");
                    alert.setHeaderText(null);
                    alert.setContentText("Unable To Edit Customer" + selectedCustomer.getFirstName() + " " + selectedCustomer.getLastName());
                    alert.showAndWait();
                }finally{
                    showCustomerDetail(selectedCustomer);

                }
            }

        }
        else{
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Customer Selected");
            alert.setHeaderText(null);
            alert.setContentText("Please select a person in the table.");
            alert.showAndWait();
        }
    }

    public CustomerOverviewController(){
        dbExecute = new DBExecuteCustomer();
    }

    public void loadDataFromDB(){
        Task<List<Customer>> customerListTask = new Task<List<Customer>>() {
            @Override
            protected List<Customer> call() throws Exception {
                List<Customer> tmpCustomerList = new ArrayList<>();
                for(int i = 0; i < 1; i++){
                    tmpCustomerList = dbExecute.selectFromDatabase(DBQueries.SelectQueries.Customer.SELECT_ALL_CUSTOMER);
                    updateProgress(i+1, 1);
                }
                return tmpCustomerList;
            }
        };
        progressBar.progressProperty().bind(customerListTask.progressProperty());
        customerListTask.setOnSucceeded(event -> {
            customerList = FXCollections.observableArrayList(customerListTask.getValue());
            customerTable.setItems(customerList);
            customerTable.getSelectionModel().selectFirst();
            showCustomerDetail(customerTable.getSelectionModel().getSelectedItem());
            FilteredList<Customer> filteredData = new FilteredList<Customer>(customerList,p->true);
            filterField.textProperty().addListener((observable,oldVal,newVal)->{
                filteredData.setPredicate(customer -> {
                    if (newVal == null || newVal.isEmpty()){
                        return true;
                    }
                    String lowerCase = newVal.toLowerCase();
                    if (customer.getFirstName().toLowerCase().contains(lowerCase)){
                        return true;
                    }else if (customer.getLastName().toLowerCase().contains(lowerCase)){
                        return true;
                    }else if(customer.getPhone() != null && customer.getPhone().contains(lowerCase)){
                        return true;
                    }
                    return false;
                });
                customerTable.setItems(filteredData);
            });
        });

        customerListTask.setOnFailed(event -> {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Unable to grab data from database!\n" + event.toString());
            alert.setTitle("Database Error");
            alert.showAndWait();
        });
        executor.execute(customerListTask);
    }

    @Override
    public void setMainClass(SaleSystem saleSystem) {
        this.saleSystem = saleSystem;
        loadDataFromDB();
    }

    @Override
    public void setDialogStage(Stage stage){
        this.dialogStage = stage;
    }

    public void showCustomerDetail(Customer customer){
        if(customer != null){
            firstNameLabel.setText(customer.getFirstName());
            lastNameLabel.setText(customer.getLastName());
            streetLabel.setText(customer.getStreet());
            postalCodeLabel.setText(customer.getPostalCode());
            cityLabel.setText(customer.getCity());
            phoneLabel.setText(customer.getPhone());
            classLabel.setText(customer.getUserClass());
            emailLabel.setText(customer.getEmail());
            storeCreditLabel.setText(String.valueOf(customer.getStoreCredit()));
            companyLabel.setText(customer.getCompany());
            pstNumberLabel.setText(customer.getPstNumber());
        }
        else{
            firstNameLabel.setText("");
            lastNameLabel.setText("");
            streetLabel.setText("");
            postalCodeLabel.setText("");
            cityLabel.setText("");
            phoneLabel.setText("");
            classLabel.setText("");
            emailLabel.setText("");
            storeCreditLabel.setText("");
            companyLabel.setText("");
            pstNumberLabel.setText("");
        }
    }
}
