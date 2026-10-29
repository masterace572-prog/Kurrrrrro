<?= $this->extend('Layout/Starter') ?>
<?= $this->section('content') ?>

<div class="container-fluid px-4 py-6">
    <div class="max-w-4xl mx-auto">
        <!-- Message Status -->
        <div class="mb-6">
            <?= $this->include('Layout/msgStatus') ?>
        </div>
        
        <!-- Key Edit Card -->
        <div class="glass-card rounded-2xl p-6 sm:p-8 animate-fade-in-up">
            <div class="flex items-center justify-between mb-6">
                <div class="flex items-center">
                    <div class="gradient-bg w-12 h-12 rounded-xl flex items-center justify-center mr-4">
                        <svg class="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"></path>
                        </svg>
                    </div>
                    <h2 class="text-2xl font-bold text-gray-800">Key Information</h2>
                </div>
                
                <div class="flex space-x-2">
                    <a href="<?= site_url('keys/generate') ?>" class="px-4 py-2 bg-gradient-to-r from-green-600 to-teal-600 text-white rounded-lg hover:from-green-500 hover:to-teal-500 transition-all">
                        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 6v6m0 0v6m0-6h6m-6 0H6"></path>
                        </svg>
                    </a>
                    <a href="<?= site_url('keys') ?>" class="px-4 py-2 bg-gradient-to-r from-purple-600 to-pink-600 text-white rounded-lg hover:from-purple-500 hover:to-pink-500 transition-all">
                        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z"></path>
                        </svg>
                    </a>
                </div>
            </div>
            
            <?= form_open('keys/edit', ['class' => 'space-y-6']) ?>
                <input type="hidden" name="id_keys" value="<?= $key->id_keys ?>">
                
                <?php if (($user->level == 1) || ($user->level == 2)) : ?>
                    <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                        <!-- Game -->
                        <div>
                            <label class="block text-sm font-medium text-gray-700 mb-2">Game</label>
                            <input type="text" name="game" value="<?= old('game') ?: $key->game ?>" class="w-full px-4 py-3 bg-white/50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500 transition-all" placeholder="Game name">
                            <?php if ($validation->hasError('game')) : ?>
                                <small class="text-red-500 text-xs mt-1"><?= $validation->getError('game') ?></small>
                            <?php endif; ?>
                        </div>
                        
                        <!-- User Key -->
                        <div>
                            <label class="block text-sm font-medium text-gray-700 mb-2">User Key</label>
                            <input type="text" name="user_key" value="<?= old('user_key') ?: $key->user_key ?>" class="w-full px-4 py-3 bg-white/50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500 transition-all font-mono" placeholder="Key">
                            <?php if ($validation->hasError('user_key')) : ?>
                                <small class="text-red-500 text-xs mt-1"><?= $validation->getError('user_key') ?></small>
                            <?php endif; ?>
                        </div>
                        
                        <!-- Duration -->
                        <div>
                            <label class="block text-sm font-medium text-gray-700 mb-2">Duration <span class="text-gray-500 text-xs">(in hours)</span></label>
                            <input type="number" name="duration" value="<?= old('duration') ?: $key->duration ?>" class="w-full px-4 py-3 bg-white/50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500 transition-all" placeholder="3">
                            <?php if ($validation->hasError('duration')) : ?>
                                <small class="text-red-500 text-xs mt-1"><?= $validation->getError('duration') ?></small>
                            <?php endif; ?>
                        </div>
                        
                        <!-- Max Devices -->
                        <div>
                            <label class="block text-sm font-medium text-gray-700 mb-2">Max Devices</label>
                            <input type="number" name="max_devices" id="max_devices" value="<?= old('max_devices') ?: $key->max_devices ?>" class="w-full px-4 py-3 bg-white/50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500 transition-all" placeholder="3">
                            <?php if ($validation->hasError('max_devices')) : ?>
                                <small class="text-red-500 text-xs mt-1"><?= $validation->getError('max_devices') ?></small>
                            <?php endif; ?>
                        </div>
                    </div>
                <?php endif; ?>
                
                <div class="grid grid-cols-1 md:grid-cols-2 gap-6">
                    <!-- Status -->
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-2">Status</label>
                        <?php $sel_status = ['' => '— Select Status —', '0' => 'Banned/Block', '1' => 'Active',]; ?>
                        <?= form_dropdown(['class' => 'w-full px-4 py-3 bg-white/50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500 transition-all', 'name' => 'status', 'id' => 'status'], $sel_status, $key->status) ?>
                        <?php if ($validation->hasError('status')) : ?>
                            <small class="text-red-500 text-xs mt-1"><?= $validation->getError('status') ?></small>
                        <?php endif; ?>
                    </div>
                    
                    <!-- Registrator -->
                    <div>
                        <label class="block text-sm font-medium text-gray-700 mb-2">Registrator</label>
                        <input type="text" name="registrator" id="registrator" value="<?= old('registrator') ?: $key->registrator ?>" class="w-full px-4 py-3 bg-white/50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500 transition-all" placeholder="Username">
                        <?php if ($validation->hasError('registrator')) : ?>
                            <small class="text-red-500 text-xs mt-1"><?= $validation->getError('registrator') ?></small>
                        <?php endif; ?>
                    </div>
                </div>
                
                <!-- Expired Date -->
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-2">Expired <?= !$key->expired_date ? '(Not started yet)' : '' ?></label>
                    <input type="text" name="expired_date" id="expired_date" value="<?= old('expired_date') ?: $key->expired_date ?>" class="w-full px-4 py-3 bg-white/50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500 transition-all" placeholder="<?= $time::now() ?>">
                    <?php if ($validation->hasError('expired_date')) : ?>
                        <small class="text-red-500 text-xs mt-1"><?= $validation->getError('expired_date') ?></small>
                    <?php endif; ?>
                </div>
                
                <!-- Devices -->
                <div>
                    <label class="block text-sm font-medium text-gray-700 mb-2">
                        Devices 
                        <span class="px-2 py-1 bg-gradient-to-r from-blue-100 to-indigo-100 text-blue-700 rounded-lg text-xs font-semibold maxDev">
                            <?= $key_info->total ?>/<?= $key->max_devices ?>
                        </span>
                        <span class="text-gray-500 text-xs">(Separately with enter)</span>
                    </label>
                    <textarea name="devices" id="devices" rows="<?= ($key_info->total > $key->max_devices) ? 3 : $key_info->total ?>" class="w-full px-4 py-3 bg-white/50 border border-gray-200 rounded-xl focus:outline-none focus:ring-2 focus:ring-purple-500 transition-all font-mono text-sm"><?= old('devices') ?: ($key_info->total ? $key_info->devices : '') ?></textarea>
                    <?php if ($validation->hasError('devices')) : ?>
                        <small class="text-red-500 text-xs mt-1"><?= $validation->getError('devices') ?></small>
                    <?php endif; ?>
                </div>
                
                <!-- Submit Button -->
                <button type="submit" class="btnUpdate w-full py-4 px-6 bg-gradient-to-r from-purple-600 to-pink-600 text-white font-bold rounded-xl hover:from-purple-500 hover:to-pink-500 transition-all duration-300 shadow-lg disabled:opacity-50 disabled:cursor-not-allowed" disabled>
                    Update User Key
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

<?= $this->section('js') ?>
<script>
    $(document).ready(function() {
        var level = "<?= $user->level ?>";
        if (level != 1) $("#registrator, #expired_date, #devices").attr('disabled', true);
        $("input, select, textarea").change(function() {
            $(".btnUpdate").attr('disabled', false);
        });
    });
    
    var total = "<?= $key_info->total ?>";
    $("#max_devices").change(function() {
        $(".maxDev").html(total + '/' + $(this).val());
        $("#devices").attr('rows', $(this).val());
    });
</script>
<?= $this->endSection() ?>