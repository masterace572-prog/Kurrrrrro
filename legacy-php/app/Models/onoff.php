<?php

namespace App\Models;

use CodeIgniter\Model;

class onoff extends Model
{
    /*=================================================================*/
    
    protected $table      = 'onoff';
    protected $allowedFields = ['status', 'myinput'];
    
}