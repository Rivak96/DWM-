// CarServiceAidl.aidl
package com.tw.carinfoservice;
import com.tw.carinfoservice.CarServiceCallBack;
import android.os.Bundle;
// Declare any non-default types here with import statements

interface CarServiceAidl {
    void registerCarServiceCallBack(in CarServiceCallBack mCarServiceCallBack);
    void unRegisterCarServiceCallBack(in CarServiceCallBack mCarServiceCallBack);
    /**
        * 请求数据
        * @param
        */
    void requestData();

    /**
         * 请求电频电压标识位 -1 为无效
         * @param
         */
    float getElectricVoltage();
    /**
         * 请求大灯标识位 -1 为无效
         * @param
         */
    int getHeadlight();
    /**
         * 请求行驶时长标识位 -1 为无效
         * @param
         */
    int getDrivingTime();
     /**
         * 请求总里程标识位  数组0位 -1 为无效  数组1位为单位0为KM公里 1为MI英里
         * @param
         */
     int[] getTotal_Mileage();

     /**
         * 请求续航里程标识位 数组0位 -1 为无效  数组1位为单位0为KM公里 1为MI英里
         * @param
         */
     int[] getRecharge_Mileage();

     /**
         * 请求平均油耗标识位 -1 为无效
         * @param
         */
     float getAverage_Fuel_Consumption();

     /**
         * 请求瞬时油耗标识位 -1 为无效
         * @param
         */
     float getInstant_Fuel_Consumption();

     /**
         * 请求档位信息标识位 -1 为无效 0：D 1:N 2:R 3:P 4:S
         * @param
         */
     int getGear_Information();

     /**
         * 请求主驾驶安全带标识位 -1 为无效
         * @param
         */
     int getMain_Driving_Seat_Belt();

     /**
         * 请求副驾驶安全带标识位 -1 为无效
         * @param
         */
     int getCo_Pilot_Seat_Belt();

     /**
         * 请求手刹标识位 -1 为无效
         * @param
         */
     int getHandbrake();

     /**
         * 请求左转向灯标识位 -1 为无效
         * @param
         */
     int getLeft_Turn_Signal();
     /**
         * 请求右转向灯标识位 -1 为无效
         * @param
         */
     int getRight_Turn_Signal();

     /**
              * 请求双闪标识位 -1 为无效
              * @param
              */
     int getDouble_flash();

     /**
         * 请求瞬时车速标识位 数组0位 -1 为无效  数组1位为单位0为KM公里 1为MI英里
         * @param
         */
     int[] getInstantaneous_Speed();

     /**
         * 请求发动机转速标识位 -1 为无效
         * @param
         */
     int getEngine_Speed();

     /**
         * 请求水温信息标识位 数组0位 -1 为无效  数组1位为单位0为KM公里 1为MI英里
         * @param
         */
     float[] getWater_Temp();

     /**
         * 请求环境温度标识位 数组0位 -1 为无效 数组1位为数值  数组2位为单位0为摄氏度 1为华氏度
         * @param
         */
     float[] getAmbient_Temp();

     /**
         * 请求机油温度标识位 数组0位 -1 为无效 数组1位为数值 数组2位为单位0为摄氏度 1为华氏度
         * @param
         */
     float[] getEngine_Oil_Temp();

     /**
          * 请求油量标识位 -1 为无效
          * @param
          */
      float getOil_Volume();


    /**
        * 请求充电量标识位 -1 为无效
        * @param
        */
    float getElectricity();
    /**
        * 请求充电状态标识位 -1 为无效
        * @param
        */
    int getCharging();

    /**
        * 请求电压警告标识位 -1 为无效
        * @param
        */
    int getVoltageWarning();

    /**
        * 请求倒车状态标识位 -1 为无效
        * @param
        */
    int getCarReverse();
    /**
        * 请求车门标识位 数组0位 -1 为无效  数组1位为单位0为KM公里 1为MI英里
        * @param
        */
    int getDoor();
   

  /**
   * 请求原车电压标识位 -1 为无效
   * @param
   */
   float getOriginalCarVoltage();
  /**
   * 请求波箱温度标识位 -1 为无效
   * @param
   */
   float getTransTemp();
  /**
   * 请求进气温度标识位 -1 为无效
   * @param
   */
   float getIntkeTemp();
  /**
   * 请求进气压力标识位 -1 为无效
   * @param
   */
   float getIntkePressure();
  /**
   * 请求涡轮增压标识位 -1 为无效
   * @param
   */
   int getTurbocharged();
  /**
   * 请求驱动模式标识位 -1 为无效
   * @param
   */
   int getDrivingMode();
  /**
   * 请求Off标志标识位 -1 为无效
   * @param
   */
   int getOffSign();
  /**
   * 请求On标志标识位 -1 为无效
   * @param
   */
   int getOnSign();
  /**
   * 请求胎压标识位 数组0,1,2,3位为胎压数据-1为无效 数组4位为单位0为psi 1为kpa 2为bar
   * @param
   */
   float[] getTirePressure();
  
  /**
   * 请求节气门标识位 -1 为无效
   * @param
   */
   int getThrottle();
  /**
   * 请求方向盘角度标识位 -1 为无效
   * @param
   */
   int getSteeringWheelAngle();
  /**
   * 请求引擎负荷标识位 -1 为无效
   * @param
   */
   int getEngineLoad();
   /**
      * 请求前后角度标识位 -1 为无效
      * @param
      */
  int getFRAngle ();
  /**
     * 请求左右角度标识位 -1 为无效
     * @param
     */
  int getLRAngle();

    /**
         * 请求保养里程标识位 数组0位 -1 为无效  数组1位为单位0为KM公里 1为MI英里
         * @param
         */
      int[] getMaintenanceMileage();
  /**
       * 请求保养时间标识位 -1 为无效  剩余多少月
       * @param
       */
    int getMaintenanceTime();
 /**
       * 请求油量警告标识位 -1 为无效
       * @param
       */
    int getOilWarning();
    /**
           * 关闭车速转速 轨迹发送
           * @param
           */
    void closureData();

    /**
               *打开车速转速 轨迹发送
               * @param
               */
        void getData();
 /**
       * 请求行驶里程 数组0位 -1 为无效  数组1位为单位0为KM公里 1为MI英里
       * @param
       */
    int[] getDrivenDistance();


    /**
       * 请求左前胎压警告标识位 数组0位 -1 为无效   数组1位为警告值
       * @param
       */
   int[] getLFTireWarning();
   /**
      * 请求右前胎压警告标识位 数组0位 -1 为无效   数组1位为警告值
      * @param
      */
   int[] getRFTireWarning();
  /**
     * 请求左后胎压警告标识位 -数组0位 -1 为无效   数组1位为警告值
     * @param
     */
   int[] getLRTireWarning();
 /**
    * 请求右后胎压警告标识位 数组0位 -1 为无效   数组1位为警告值
    * @param
    */
   int[] getRRTireWarning();

   /**
      * 请求左前胎压温度标识位 数组0位 -1 为无效 数组1位为数值 数组2位为单位0为摄氏度 1为华氏度
      * @param
      */
  float[] getLFTireTemp();
  /**
     * 请求右前胎压温度标识位 数组0位 -1 为无效 数组1位为数值 数组2位为单位0为摄氏度 1为华氏度
     * @param
     */
  float[] getRFTireTemp();
 /**
    * 请求左后胎压温度标识位 数组0位 -1 为无效 数组1位为数值 数组2位为单位0为摄氏度 1为华氏度
    * @param
    */
  float[] getLRTireTemp();
/**
   * 请求右后胎压温度标识位 数组0位 -1 为无效 数组1位为数值 数组2位为单位0为摄氏度 1为华氏度
   * @param
   */
  float[] getRRTireTemp();
  /**
         * 请求清洗液警告标识位 -1 为无效
         * @param
         */
   int getWashingFluidWarning();

   /**
      * 请求雷达数据 1-11从近到远 0没有 16个数据 分别是前左，前左中，前右中 前右，右前，右前中，右后中，右后,后右，后右中，后左中，后左，左后，左后中，左前中，左前，从前左顺时针
      * @param
      */
   int[] getRadar( );
   /**
      * 请求轨迹数据 中间值为240 0-480
      * @param
      */
   int getTrack();

   /**
       * 请求转向灯标识位 -1 为无效 1为右转向 2为左转向 3为双闪
       * @param
       */
   int getTurn_Signal();
   /**
        * 请求本次驾驶平均油耗标识位 -1 为无效
        * @param
        */
   float getAverage_Fuel_Consumption_For_This_Drive();


   /**
       *扩展接口，后期接口扩展
       */
   Bundle extendedInterface(in Bundle bundle);

    /**
       *暂停或者打开 服务端对当前客户端的数据发送 true为打开 fales为暂停 默认为true打开的
       */
   void pauseData(in CarServiceCallBack mCarServiceCallBack, boolean is);

    /**
       *同步更新接口第三方更新时同步更新CarInfoService自身接口
       */
   void updateApk();
}
