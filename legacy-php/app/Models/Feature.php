<?php

namespace App\Models;

use CodeIgniter\Model;

class Feature extends Model
{
    /*=================================================================*/
    
    protected $table      = 'Feature';
    protected $allowedFields = ['ESP', 'SilentAim', 'AIM','Item', 'Memory', 'BulletTrack', 'Floating', 'Setting'];
    
}