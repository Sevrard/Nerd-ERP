package com.nerd.erp.application.customer;

import com.nerd.erp.domain.customer.Customer;
import com.nerd.erp.domain.customer.CustomerType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CustomerSnapshotFactory {

    private CustomerSnapshotFactory() {
    }

    public static Map<String, Object> fromCustomer(Customer customer) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("customerId", customer.getId().toString());
        snapshot.put("customerType", customer.getCustomerType().name());
        if (customer.getCustomerType() == CustomerType.COMPANY) {
            snapshot.put("displayName", customer.getCompanyName());
        } else {
            snapshot.put("displayName", ((customer.getFirstName() != null ? customer.getFirstName() : "") + " "
                    + (customer.getLastName() != null ? customer.getLastName() : "")).trim());
        }
        snapshot.put("companyName", customer.getCompanyName());
        snapshot.put("firstName", customer.getFirstName());
        snapshot.put("lastName", customer.getLastName());
        snapshot.put("email", customer.getEmail());
        snapshot.put("phone", customer.getPhone());
        snapshot.put("addressLine1", customer.getAddressLine1());
        snapshot.put("addressLine2", customer.getAddressLine2());
        snapshot.put("postalCode", customer.getPostalCode());
        snapshot.put("city", customer.getCity());
        snapshot.put("countryCode", customer.getCountryCode());
        return Collections.unmodifiableMap(snapshot);
    }
}
