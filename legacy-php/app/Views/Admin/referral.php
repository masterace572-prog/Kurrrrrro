<?php
include('conn.php');
include('mail.php');

// For Highest id Ref
$sqli = "SELECT * FROM referral_code
ORDER BY id_reff DESC
LIMIT 1;";
$result = mysqli_query($conn, $sqli);
$id_reff = mysqli_fetch_assoc($result);

// For Referral Code
$sql = "SELECT Referral FROM referral_code";
$result = mysqli_query($conn, $sql);
$refcode = mysqli_fetch_assoc($result);
$row = $refcode;

?>

<?= $this->extend('Layout/Starter') ?>
<?= $this->section('content') ?>

<div class="container-fluid px-4 py-6">
    <!-- Message Status -->
    <div class="mb-6">
        <?= $this->include('Layout/msgStatus') ?>
    </div>
    
    <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <!-- Generate Referral Card -->
        <div class="glass-card rounded-2xl p-6 sm:p-8 animate-fade-in-up">
            <div class="flex items-center mb-6">
                <div class="gradient-bg w-12 h-12 rounded-xl flex items-center justify-center mr-4">
                    <svg class="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6"></path>
                    </svg>
                </div>
                <h2 class="text-2xl font-bold text-gray-800">Generate <?= $title ?></h2>
            </div>
            
            <?= form_open('', ['class' => 'space-y-6']) ?>
                <!-- Saldo -->
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-2">Set Saldo</label>
                    <div class="relative">
                        <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                            <svg class="w-5 h-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                            </svg>
                        </div>
                        <input type="number" name="set_saldo" value="5" min="1" max="99999" class="w-full pl-10 pr-4 py-3 bg-white/50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500 transition-all">
                    </div>
                    <?php if ($validation->hasError('set_saldo')) : ?>
                        <small class="text-red-500 text-xs mt-1"><?= $validation->getError('set_saldo') ?></small>
                    <?php endif; ?>
                </div>
                
                <!-- Account Expiration -->
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-2">Account Expiration</label>
                    <?= form_dropdown(['class' => 'w-full px-4 py-3 bg-white/50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500 transition-all', 'name' => 'accExpire', 'id' => 'accExpire'], $accExpire, old('accExpire') ?: '') ?>
                    <?php if ($validation->hasError('accExpire')) : ?>
                        <small class="text-red-500 text-xs mt-1"><?= $validation->getError('accExpire') ?></small>
                    <?php endif; ?>
                </div>
                
                <!-- Account Level -->
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-2">Account Level</label>
                    <?= form_dropdown(['class' => 'w-full px-4 py-3 bg-white/50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500 transition-all', 'name' => 'accLevel', 'id' => 'accLevel'], $accLevel, old('accLevel') ?: '') ?>
                    <?php if ($validation->hasError('accLevel')) : ?>
                        <small class="text-red-500 text-xs mt-1"><?= $validation->getError('accLevel') ?></small>
                    <?php endif; ?>
                </div>
                
                <!-- Submit Button -->
                <button type="submit" class="w-full py-3 px-6 bg-gradient-to-r from-purple-600 to-pink-600 text-white font-bold rounded-xl hover:from-purple-500 hover:to-pink-500 transition-all duration-300 shadow-lg">
                    Create Referral Code
                </button>
            <?= form_close() ?>
        </div>
        
        <!-- History Table -->
        <div class="lg:col-span-2 glass-card rounded-2xl p-6 sm:p-8 animate-fade-in-up" style="animation-delay: 0.1s">
            <div class="flex items-center mb-6">
                <div class="gradient-bg-3 w-12 h-12 rounded-xl flex items-center justify-center mr-4">
                    <svg class="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                    </svg>
                </div>
                <h2 class="text-2xl font-bold text-gray-800">History Generate - Total <?= $total_code ?></h2>
            </div>
            
            <?php if ($code) : ?>
                <div class="overflow-x-auto">
                    <table class="w-full">
                        <thead>
                            <tr class="border-b-2 border-gray-200">
                                <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700">ID</th>
                                <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700">Referral</th>
                                <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700">Hashed</th>
                                <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700">Saldo</th>
                                <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700">Level</th>
                                <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700">Expiration</th>
                                <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700">Used by</th>
                                <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700">Create by</th>
                            </tr>
                        </thead>
                        <tbody>
                            <?php foreach ($code as $c) : ?>
                                <tr class="border-b border-gray-100 hover:bg-gray-50 transition-colors">
                                    <td class="px-4 py-3 text-sm text-gray-700"><?= $c->id_reff ?></td>
                                    <td class="px-4 py-3">
                                        <span class="px-2 py-1 bg-purple-100 text-purple-700 rounded-lg text-xs font-semibold">
                                            <?= $c->Referral ?>
                                        </span>
                                    </td>
                                    <td class="px-4 py-3 text-sm text-gray-500 font-mono"><?= substr($c->code, 1, 15) ?></td>
                                    <td class="px-4 py-3">
                                        <span class="px-2 py-1 bg-green-100 text-green-700 rounded-lg text-xs font-semibold">
                                            ₹<?= $c->set_saldo ?>
                                        </span>
                                    </td>
                                    <td class="px-4 py-3 text-sm text-gray-700"><?= $c->level ?></td>
                                    <td class="px-4 py-3 text-sm text-gray-700"><?= $c->acc_expiration ?></td>
                                    <td class="px-4 py-3 text-sm text-gray-700"><?= $c->used_by ?></td>
                                    <td class="px-4 py-3 text-sm text-gray-700"><?= $c->created_by ?></td>
                                </tr>
                            <?php endforeach; ?>
                        </tbody>
                    </table>
                </div>
            <?php else : ?>
                <p class="text-center text-gray-500 py-8">No referral codes generated yet</p>
            <?php endif; ?>
        </div>
    </div>
</div>

<style>
    @keyframes fadeInUp {
        from {
            opacity: 0;
            transform: translateY(30px);
        }
        to {
            opacity: 1;
            transform: translateY(0);
        }
    }
    
    .animate-fade-in-up {
        animation: fadeInUp 0.6s ease-out forwards;
    }
    
    .glass-card {
        background: rgba(255, 255, 255, 0.95);
        backdrop-filter: blur(10px);
        border: 1px solid rgba(255, 255, 255, 0.3);
        box-shadow: 0 8px 32px 0 rgba(31, 38, 135, 0.15);
    }
    
    .gradient-bg {
        background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    }
    
    .gradient-bg-3 {
        background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
    }
</style>

<?= $this->endSection() ?>