<?php
include('mail.php');
?>

<?= $this->extend('Layout/Starter') ?>
<?= $this->section('content') ?>

<div class="container-fluid px-4 py-6">
    <div class="max-w-4xl mx-auto">
        <!-- Message Status -->
        <div class="mb-6">
            <?= $this->include('Layout/msgStatus') ?>
        </div>
        
        <!-- User Edit Card -->
        <div class="glass-card rounded-2xl p-6 sm:p-8 animate-fade-in-up">
            <div class="flex items-center justify-between mb-6">
                <div class="flex items-center">
                    <div class="gradient-bg w-12 h-12 rounded-xl flex items-center justify-center mr-4">
                        <svg class="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z"></path>
                        </svg>
                    </div>
                    <div>
                        <h2 class="text-2xl font-bold text-gray-800">Account Information</h2>
                        <p class="text-sm text-gray-600"><?= getName($target) ?></p>
                    </div>
                </div>
                
                <a href="<?= site_url('admin/manage-users') ?>" class="px-4 py-2 bg-gradient-to-r from-gray-600 to-gray-700 text-white rounded-lg hover:from-gray-500 hover:to-gray-600 transition-all flex items-center space-x-2">
                    <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 19l-7-7m0 0l7-7m-7 7h18"></path>
                    </svg>
                    <span>Back</span>
                </a>
            </div>
            
            <?= form_open('', ['class' => 'space-y-6']) ?>
                <input type="hidden" name="user_id" value="<?= $target->id_users ?>">
                
                <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                    <!-- Username -->
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-2">Username</label>
                        <input type="text" name="username" value="<?= old('username') ?: $target->username ?>" class="w-full px-4 py-3 bg-white/50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500 transition-all" placeholder="Username">
                        <?php if ($validation->hasError('username')) : ?>
                            <small class="text-red-500 text-xs mt-1"><?= $validation->getError('username') ?></small>
                        <?php endif; ?>
                    </div>
                    
                    <!-- Fullname -->
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-2">Fullname</label>
                        <input type="text" name="fullname" value="<?= old('fullname') ?: $target->fullname ?>" class="w-full px-4 py-3 bg-white/50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500 transition-all" placeholder="Full name">
                        <?php if ($validation->hasError('fullname')) : ?>
                            <small class="text-red-500 text-xs mt-1"><?= $validation->getError('fullname') ?></small>
                        <?php endif; ?>
                    </div>
                    
                    <!-- Roles -->
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-2">Roles</label>
                        <?php $sel_level = ['' => '— Select Roles —', '1' => 'Owner', '2' => 'Admin', '3' => 'Reseller']; ?>
                        <?= form_dropdown(['class' => 'w-full px-4 py-3 bg-white/50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500 transition-all', 'name' => 'level', 'id' => 'level'], $sel_level, $target->level) ?>
                    </div>
                    
                    <!-- Status -->
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-2">Status</label>
                        <?php $sel_status = ['' => '— Select Status —', '2' => 'Banned/Block', '1' => 'Active', '3' => 'Expired',]; ?>
                        <?= form_dropdown(['class' => 'w-full px-4 py-3 bg-white/50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500 transition-all', 'name' => 'status', 'id' => 'status'], $sel_status, $target->status) ?>
                    </div>
                    
                    <!-- Saldo -->
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-2">Saldo</label>
                        <div class="relative">
                            <div class="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                                <svg class="w-5 h-5 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8c-1.657 0-3 .895-3 2s1.343 2 3 2 3 .895 3 2-1.343 2-3 2m0-8c1.11 0 2.08.402 2.599 1M12 8V7m0 1v8m0 0v1m0-1c-1.11 0-2.08-.402-2.599-1M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
                                </svg>
                            </div>
                            <input type="number" name="saldo" value="<?= old('saldo') ?: $target->saldo ?>" class="w-full pl-10 pr-4 py-3 bg-white/50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500 transition-all" placeholder="0">
                        </div>
                        <?php if ($validation->hasError('saldo')) : ?>
                            <small class="text-red-500 text-xs mt-1"><?= $validation->getError('saldo') ?></small>
                        <?php endif; ?>
                    </div>
                    
                    <!-- Uplink -->
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-2">Uplink</label>
                        <input type="text" name="uplink" value="<?= old('uplink') ?: $target->uplink ?>" class="w-full px-4 py-3 bg-white/50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500 transition-all" placeholder="Uplink">
                        <?php if ($validation->hasError('uplink')) : ?>
                            <small class="text-red-500 text-xs mt-1"><?= $validation->getError('uplink') ?></small>
                        <?php endif; ?>
                    </div>
                    
                    <!-- Expiration -->
                    <div class="md:col-span-2">
                        <label class="block text-sm font-medium text-gray-700 mb-2">Expiration Date</label>
                        <input type="text" name="expiration" value="<?= old('expiration') ?: $target->expiration_date ?>" class="w-full px-4 py-3 bg-white/50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500 transition-all" placeholder="YYYY-MM-DD HH:MM:SS">
                        <?php if ($validation->hasError('expiration')) : ?>
                            <small class="text-red-500 text-xs mt-1"><?= $validation->getError('expiration') ?></small>
                        <?php endif; ?>
                    </div>
                </div>
                
                <!-- Submit Button -->
                <button type="submit" class="w-full py-4 px-6 bg-gradient-to-r from-purple-600 to-pink-600 text-white font-bold rounded-xl hover:from-purple-500 hover:to-pink-500 transition-all duration-300 shadow-lg">
                    Update Account Information
                </button>
            <?= form_close() ?>
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
</style>

<?= $this->endSection() ?>