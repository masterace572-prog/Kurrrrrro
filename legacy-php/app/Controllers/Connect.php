<?php

namespace App\Controllers;

use App\Models\KeysModel;

class Connect extends BaseController
{
    protected $model, $game, $uKey, $sDev;

    public function __construct()
    {
        include('conn.php');
//=================================================
        $sql1 ="select * from onoff where id=1";
        $result1 = mysqli_query($conn, $sql1);
        $userDetails1 = mysqli_fetch_assoc($result1);
//=================================================
        $this->model = new KeysModel();
//=================================================
        if($userDetails1['status'] == 'on')
        {
            $this->maintenance = true;
        } else {
            $this->maintenance = false;
        }
//=================================================
       $this->staticWords = "Vm8Lk7Uj2JmsjCPVPVjrLa7zgfx3uz9E";
    }

    public function index()
    {
        if ($this->request->getPost()) {
            return $this->index_post();
        } else {
            $nata = [
                "web_info" => [
                    "_client" => BASE_NAME,
                    "license" => "Qp5KSGTquetnUkjX6UVBAURH8hTkZuLM",
                    "version" => "1.0.0",
                ],
                "web__dev" => [
                    "author" => "@",
                    "telegram" => "https://t.me/"
                           ],
            ];
            
            return "<h1><strong><center><font size='10' color='red' face='arial'><marquee direction='right' scrollamount='15'>WANT OWN KURO PANEL?<br> DM HERE - </marquee></font></center></strong></h1>";
            $this->response->setJSON($nata);
        }
    }

    public function index_post()
    {
        $isMT = $this->maintenance;
        $game = $this->request->getPost('game');
        $uKey = $this->request->getPost('user_key');
        $sDev = $this->request->getPost('serial');

        $form_rules = [
            'game' => 'required|alpha_dash',
            'user_key' => 'required|min_length[1]|max_length[36]',
            'serial' => 'required|alpha_dash'
        ];

        if (!$this->validate($form_rules)) {
            $data = [
                'status' => false,
                'reason' => "Bad Parameter",
            ];
            return $this->response->setJSON($data);
        }

        if ($isMT) {
            
            include('conn.php');
        
            $sql1 ="select * from onoff where id=1";
            $result1 = mysqli_query($conn, $sql1);
            $userDetails1 = mysqli_fetch_assoc($result1);
        
            
            $data = [
                'status' => true,
                'reason' => $userDetails1['myinput']
            ];
        } else {
            if (!$game or !$uKey or !$sDev) {
                $data = [
                    'status' => false,
                    'reason' => 'INVALID PARAMETER'
                ];
            } else {
                $time = new \CodeIgniter\I18n\Time;
                $model = $this->model;
                $findKey = $model
                    ->getKeysGame(['user_key' => $uKey, 'game' => $game]);

                if ($findKey) {
                    if ($findKey->status != 1) {
                        $data = [
                            'status' => false,
                            'reason' => 'USER BLOCKED'
                        ];
                    } else {
                        $id_keys = $findKey->id_keys;
                        $duration = $findKey->duration;
                        $expired = $findKey->expired_date;
                        $max_dev = $findKey->max_devices;
                        $devices = $findKey->devices;
    
                        function checkDevicesAdd($serial, $devices, $max_dev)
                        {
                            $lsDevice = explode(",", $devices);
                            $cDevices = isset($devices) ? count($lsDevice) : 0;
                            $serialOn = in_array($serial, $lsDevice);
    
                            if ($serialOn) {
                                return true;
                            } else {
                                if ($cDevices < $max_dev) {
                                    array_push($lsDevice, $serial);
                                    $setDevice = reduce_multiples(implode(",", $lsDevice), ",", true);
                                    return ['devices' => $setDevice];
                                } else {
                                    // ! false - devices max
                                    return false;
                                }
                            }
                        }
    
                        if (!$expired) {
                            $setExpired = $time::now()->addHours($duration);
                            $model->update($id_keys, ['expired_date' => $setExpired]);
                            $data['status'] = true;
                        } else {
                            if ($time::now()->isBefore($expired)) {
                                $data['status'] = true;
                            } else {
                                $data = [
                                    'status' => false,
                                    'reason' => 'EXPIRED KEY'
                                ];
                            }
                        }
    
                        if ($data['status']) {
                            
                            include('conn.php');
        
                            $sql2 ="select * from modname where id=1";
                            $result2 = mysqli_query($conn, $sql2);
                            $userDetails2 = mysqli_fetch_assoc($result2);
                            
                            $sql3 ="select * from _ftext where id=1";
                            $result3 = mysqli_query($conn, $sql3);
                            $userDetails3 = mysqli_fetch_assoc($result3);
                            
                            $sql4 = "SELECT expired_date FROM keys_code WHERE user_key='$uKey'";
                            $result4 = mysqli_query($conn, $sql4);
                            $userDetails4 = mysqli_fetch_assoc($result4);
//=================================================
        $sql = "SELECT * FROM Feature WHERE id=1";
        $result = mysqli_query($conn, $sql);
        $ModFeatureStatus = mysqli_fetch_assoc($result);
//=================================================
        $rngcnt = $time->getTimestamp();
//=================================================
                            $devicesAdd = checkDevicesAdd($sDev, $devices, $max_dev);
                            if ($devicesAdd) {
                                if (is_array($devicesAdd)) {
                                    $model->update($id_keys, $devicesAdd);
                                }
                                // ? game-user_key-serial-word di line 15
                                $real = "$game-$uKey-$sDev-$this->staticWords";
                                
                                $expiry = $findKey->expired_date;
                            if ($expiry == null) {
                                 $expiry = $time::now()->addDays($duration);
                            }
                            
                                $data = [
                                    'status' => true,
                                    'data' => [
                                        'real' => $real,
                                        'token' => md5($real),
                                        'modname' => $userDetails2['modname'],
                                        'mod_status' => $userDetails3['_status'],
                                        'credit' => $userDetails3['_ftext'],
                                        'ESP' => $ModFeatureStatus['ESP'],
                                        'Item' => $ModFeatureStatus['Item'],
                                        'AIM' => $ModFeatureStatus['AIM'],
                                        'SilentAim' => $ModFeatureStatus['SilentAim'],
                                        'BulletTrack' => $ModFeatureStatus['BulletTrack'],
                                        'Floating' => $ModFeatureStatus['Floating'],
                                        'Memory' => $ModFeatureStatus['Memory'],
                                        'Setting' => $ModFeatureStatus['Setting'],
                                        'expired_date' => $userDetails4['expired_date'],
                                        'EXP' => $expiry,
                                        'exdate' => $expiry,
                                        'device'=> $max_dev,
                                        'rng' => $rngcnt
                                    ],
                                ];
                            } else {
                                $data = [
                                    'status' => false,
                                    'reason' => 'MAX DEVICE REACHED'
                                ];
                            }
                        }
                    }
                } else {
                    $data = [
                        'status' => false,
                        'reason' => 'USER OR GAME NOT REGISTERED'
                    ];
                }
            }
        }
        return $this->response->setJSON($data);
    }
}
