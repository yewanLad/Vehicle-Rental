package com.rental.server;


final class AppData {
    static final VehicleList vehicleList = new VehicleList();
    static final UserList userList = new UserList();
    static final RentalList rentalList = new RentalList();
    static final PaymentList paymentList = new PaymentList();
    static final MaintenanceList maintenanceList = new MaintenanceList();
    static final FineList fineList = new FineList();

    static void loadAll() {
        vehicleList.loadFromFile();
        userList.loadFromFile();
        rentalList.loadFromFile();
        paymentList.loadFromFile();
        maintenanceList.loadFromFile();
        fineList.loadFromFile();
    }

    private AppData() { }
}
