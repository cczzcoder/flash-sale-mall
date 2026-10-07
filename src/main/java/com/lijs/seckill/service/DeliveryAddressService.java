package com.lijs.seckill.service;

import com.lijs.seckill.dao.DeliveryAddressDao;
import com.lijs.seckill.domain.DeliveryAddress;
import com.lijs.seckill.result.ResultCode;
import com.lijs.seckill.vo.DeliveryAddressVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
public class DeliveryAddressService {
    private final DeliveryAddressDao addressDao;

    public DeliveryAddressService(DeliveryAddressDao addressDao) {
        this.addressDao = addressDao;
    }

    public List<DeliveryAddress> list(long userId) {
        return addressDao.selectByUserId(userId);
    }

    public DeliveryAddress getOwned(long addressId, long userId) {
        return addressDao.selectOwned(addressId, userId);
    }

    public DeliveryAddress getDefault(long userId) {
        return addressDao.selectDefault(userId);
    }

    public boolean existsForOrder(long userId, Long addressId) {
        return addressId == null ? getDefault(userId) != null : getOwned(addressId, userId) != null;
    }

    @Transactional
    public ResultCode save(long userId, DeliveryAddressVo vo) {
        DeliveryAddress address;
        if (vo.getId() == null) {
            address = new DeliveryAddress();
            address.setUserId(userId);
            address.setCreateDate(new Date());
        } else {
            address = addressDao.selectOwned(vo.getId(), userId);
            if (address == null) {
                return ResultCode.ADDRESS_NOT_EXIST;
            }
        }
        address.setReceiverName(vo.getReceiverName().trim());
        address.setReceiverMobile(vo.getReceiverMobile().trim());
        address.setProvince(vo.getProvince().trim());
        address.setCity(vo.getCity().trim());
        address.setDistrict(vo.getDistrict().trim());
        address.setDetail(vo.getDetail().trim());
        address.setDefaultAddress(Boolean.TRUE.equals(vo.getDefaultAddress()));
        address.setUpdateDate(new Date());
        if (address.getId() == null) {
            addressDao.insert(address);
        } else {
            addressDao.updateById(address);
        }
        if (Boolean.TRUE.equals(vo.getDefaultAddress())) {
            addressDao.clearDefault(userId);
            addressDao.updateById(address);
        }
        return ResultCode.SUCCESS;
    }

    @Transactional
    public ResultCode delete(long addressId, long userId) {
        return addressDao.deleteOwned(addressId, userId) == 1
                ? ResultCode.SUCCESS : ResultCode.ADDRESS_NOT_EXIST;
    }
}
