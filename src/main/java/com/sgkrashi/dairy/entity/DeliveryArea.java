package com.sgkrashi.dairy.entity;

import com.sgkrashi.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** A pincode the farm delivers dairy to. */
@Entity
@Table(name = "delivery_areas")
public class DeliveryArea extends BaseEntity {

    @Column(name = "pincode", nullable = false, unique = true, length = 10)
    private String pincode;

    @Column(name = "label", length = 100)
    private String label;

    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}
