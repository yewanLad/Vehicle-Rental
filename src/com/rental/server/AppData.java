package com.rental.server;

import com.rental.vehicle.VehicleList;
import com.rental.user.UserList;
import com.rental.booking.RentalList;
import com.rental.payment.PaymentList;
import com.rental.maintenance.MaintenanceList;
import com.rental.fine.FineList;

/** The single in-memory copy of every module's data, shared by all handlers. */
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
