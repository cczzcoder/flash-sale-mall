package com.lijs.seckill.vo;

import com.lijs.seckill.util.ValidatorUtil;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

public class DeliveryAddressVo {
    private Long id;

    @NotBlank
    @Size(max = 32)
    private String receiverName;

    @NotBlank
    @Pattern(regexp = ValidatorUtil.REG_MOBILE_TELEPHONE)
    private String receiverMobile;

    @NotBlank
    @Size(max = 32)
    private String province;

    @NotBlank
    @Size(max = 32)
    private String city;

    @NotBlank
    @Size(max = 32)
    private String district;

    @NotBlank
    @Size(max = 128)
    private String detail;

    private Boolean defaultAddress;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getReceiverName() { return receiverName; }
    public void setReceiverName(String receiverName) { this.receiverName = receiverName; }
    public String getReceiverMobile() { return receiverMobile; }
    public void setReceiverMobile(String receiverMobile) { this.receiverMobile = receiverMobile; }
    public String getProvince() { return province; }
    public void setProvince(String province) { this.province = province; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) { this.district = district; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }
    public Boolean getDefaultAddress() { return defaultAddress; }
    public void setDefaultAddress(Boolean defaultAddress) { this.defaultAddress = defaultAddress; }
}
