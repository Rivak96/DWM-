// CarServiceCallBack.aidl
package com.tw.carinfoservice;
import android.os.Bundle;

// Declare any non-default types here with import statements

interface CarServiceCallBack {



     /**
         * 电频电压
         * @param
         */
    void onElectricVoltage(float mVoltage,String mUnit);

    /**
         * 大灯
         * @param
         */
    void onHeadlight(boolean mHeadlight);

    /**
         * 行驶时长
         * @param
         */
    void onDrivingTime(int mDrivingTime);
     /**
         * 总里程
         * @param
         */
     void onTotal_Mileage(float mTotal_Mileage,String mUnit);

     /**
         * 续航里程
         * @param
         */
     void onRecharge_Mileage(float mRecharge_Mileage,String mUnit);

     /**
         * 平均油耗
         * @param
         */
     void onAverage_Fuel_Consumption(float mAverage_Fuel_Consumption,String mUnit);

     /**
         * 瞬时油耗
         * @param
         */
     void onInstant_Fuel_Consumption(float mInstant_Fuel_Consumption,String mUnit);

     /**
         * 档位信息 0：D 1:N 2:R 3:P 4:S
         * @param
         */
     void onGear_Information(int mGear_Information);

     /**
         * 主驾驶安全带
         * @param
         */
     void onMain_Driving_Seat_Belt(boolean mMain_Driving_Seat_Belt);

     /**
         * 副驾驶安全带
         * @param
         */
     void onCo_Pilot_Seat_Belt(boolean mCo_Pilot_Seat_Belt);

     /**
         * 手刹
         * @param
         */
     void onHandbrake(boolean mHandbrake);

     /**
         * 左转向灯
         * @param
         */
     void onLeft_Turn_Signal(boolean mLeft_Turn_Signal);
     /**
         * 右转向灯
         * @param
         */
     void onRight_Turn_Signal(boolean mRight_Turn_Signal);

     /**
              * 双闪
              * @param
              */
     void onDouble_flash(boolean mDouble_flash);

     /**
         * 瞬时车速
         * @param
         */
     void onInstantaneous_Speed(int mInstantaneous_Speed);

     /**
         * 发动机转速
         * @param
         */
     void onEngine_Speed(int mEngine_Speed);

     /**
         * 水温信息
         * @param
         */
     void onWater_Temp(int mWater_Temp,String mUnit);

     /**
         * 环境温度
         * @param
         */
     void onAmbient_Temp(float mAmbient_Temp,String mUnit);

     /**
         * 机油温度
         * @param
         */
     void onEngine_Oil_Temp(float mEngine_Oil_Temp,String mUnit);

     /**
          * 油量
          * @param
          */
      void onOil_Volume(float mOil_Volume,String mUnit);


    /**
        * 充电量
        * @param
        */
    void onElectricity(float mElectricity);
    /**
        * 充电状态
        * @param
        */
    void onCharging(int mCharging);

    /**
        * 电压警告
        * @param
        */
    void onVoltageWarning(boolean mVoltageWarning);

    /**
        * 倒车状态
        * @param
        */
    void onCarReverse(boolean mCarReverse);
    /**
        * 左前门
        * @param
        */
    void onLeftFrontDoor(boolean mLeftFrontDoor);
    /**
        * 右前门
        * @param
        */
    void onRightFrontDoor(boolean mRightFrontDoor);
    /**
        * 后尾箱
        * @param
        */
    void onBackDoor(boolean mBackDoor);
     /**
         * 扩展接口
         * @param
         */
     void onExpand(in Bundle bundle);

  /**
   * 原车电压
   * @param
   */
   void onOriginalCarVoltage(float mVoltage,String mUnit);
  /**
   * 波箱温度
   * @param
   */
   void onTransTemp(float mTransTemp,String mUnit);
  /**
   * 进气温度
   * @param
   */
   void onIntkeTemp(float mIntkeTemp,String mUnit);
  /**
   * 进气压力
   * @param
   */
   void onIntkePressure(float mIntkePressure,String mUnit);
  /**
   * 涡轮增压
   * @param
   */
   void onTurbocharged(float mTurbocharged,String mUnit);
  /**
   * 驱动模式
   * @param
   */
   void onDrivingMode(int mDrivingMode);
  /**
   * Off标志
   * @param
   */
   void onOffSign(boolean mOffSign);
  /**
   * On标志
   * @param
   */
   void onOnSign(boolean mOnSign);
  /**
   * 左前胎压
   * @param
   */
   void onLFTirePressure(float mLFTirePres,String mUnit);
   /**
      * 右前胎压
      * @param
      */
   void onRFTirePressure(float mRFTirePres,String mUnit);
  /**
     * 左后胎压
     * @param
     */
   void onLRTirePressure(float mLRTirePres,String mUnit);
 /**
    * 右后胎压
    * @param
    */
   void onRRTirePressure(float mRRTirePres,String mUnit);
  /**
   * 节气门
   * @param
   */
   void onThrottle(float mThrottle,String mUnit);
  /**
   * 方向盘角度
   * @param
   */
   void onSteeringWheelAngle(float mteeringWheelAngle,String mUnit);
  /**
   * 引擎负荷
   * @param
   */
   void onEngineLoad(float mEngineLoad,String mUnit);
   /**
      * 前后角度
      * @param
      */
  void onFRAngle (float mFRAngle,String mUnit);
  /**
     * 左右角度
     * @param
     */
  void onLRAngle(float mLRAngle,String mUnit);

    /**
         * 保养里程
         * @param
         */
      void onMaintenanceMileage(float mMaintenanceMileage,String mUnit);
  /**
       * 保养时间
       * @param
       */
    void onMaintenanceTime(String mMaintenanceTime);
    /**
       * 油量警告
       * @param
       */
    void onOilWarning(boolean mOilWarning);

    /**
       * 行驶里程
       * @param
       */
    void onDrivenDistance(int mDrivenDistance,String mUnit);

    /**
       * 左前胎压警告  mTireWarningType 0表示胎压正常 1胎压异常           2温度异常                 3传感器异常

                    mTireWarningValue 0正常       1胎压过低           1温度过低                 1传感器丢失
                                                 2胎压过高           2温度过高                 2传感器电池电量低
                                                 3请检查胎压         3温度警告                 3传感器故障
                                                 4胎压失压
                                                 5系统故障
                                                 6快速泄漏
       * @param
       */
   void onLFTireWarning(int mTireWarningType,int mTireWarningValue);
   /**
      * 右前胎压警告
      * @param
      */
   void onRFTireWarning(int mTireWarningType,int mTireWarningValue);
  /**
     * 左后胎压警告
     * @param
     */
   void onLRTireWarning(int mTireWarningType,int mTireWarningValue);
 /**
    * 右后胎压警告
    * @param
    */
   void onRRTireWarning(int mTireWarningType,int mTireWarningValue);

   /**
      * 左前胎压温度
      * @param
      */
  void onLFTireTemp(int mTireTemp,String mUnit);
  /**
     * 右前胎压温度
     * @param
     */
  void onRFTireTemp(int mTireTemp,String mUnit);
 /**
    * 左后胎压温度
    * @param
    */
  void onLRTireTemp(int mTireTemp,String mUnit);
/**
   * 右后胎压温度
   * @param
   */
  void onRRTireTemp(int mTireTemp,String mUnit);

    /**
       * 清洗液警告
       * @param
       */
    void onWashingFluidWarning(boolean mWashingFluidWarning);

    /**
       * 雷达数据1-11从近到远 0没有 16个数据 分别是前左，前左中，前右中 前右，右前，右前中，右后中，右后,后右，后右中，后左中，后左，左后，左后中，左前中，左前，从前左顺时针
       * @param
       */
  void onRadar(int mFLR,int mFCLR,int mFCRR,int mFRR,int mRightFR,int mRightCFR,int mRightCRR,int mRightRR,int mRRR,int mRCRR,int mRCLR,int mRLR,int mLeftRR
                          ,int mLeftCRR,int mLeftCFR,int mLeftFR);

    /**
       * 轨迹数据 中间值为240 0-480
       * @param
       */
    void onTrack(int mTrack);
    /**
         * 转向灯 0为无转向 1为右转向 2为左转向 3为双闪
         * @param
         */
     void onTurn_Signal(int mTurn_Signal);
    /**
          * 本次驾驶平均油耗
          * @param
          */
     void onAverage_Fuel_Consumption_For_This_Drive(float mAverage_Fuel_Consumption,String mUnit);



    /**
        *扩展接口，后期接口扩展
     */
    void onExtendedInterface(in Bundle bundle);

    /**
      * 本次驾驶里程
      * @param
      */
     void onMileage_For_This_Drive(float mMileage,String mUnit);
//      /**
//          * GPS车速
//          * @param
//          */
//      void onGPS_Speed(float mGPS_Speed);
//      /**
//            * GPS方向
//            * @param
//            */
//    void onGPS_Bear(float mGPS_Bear);
    /**
        * 左前门
        * @param
        */
    void onLeftRearDoor(boolean mLeftFrontDoor);
    /**
        * 右前门
        * @param
        */
    void onRightRearDoor(boolean mRightFrontDoor);

}
