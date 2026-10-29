<?php

function color($value) {
if($value == 1) {
return "#0000FF";
} else {
return "#FF0000";
}
}
?>

<?= $this->extend('Layout/Starter') ?>
<?= $this->section('content') ?>

<div class="container-fluid px-4 py-6">
    <!-- Info Alert -->
    <div class="mb-6 bg-gradient-to-r from-blue-50 to-indigo-50 border-l-4 border-blue-500 rounded-lg p-4 animate-fade-in-up">
        <div class="flex items-center">
            <svg class="w-6 h-6 text-blue-500 mr-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z"></path>
            </svg>
            <p class="text-sm text-blue-800"><strong>INFO:</strong> Search specify user by their (username, fullname, saldo or uplink).</p>
        </div>
    </div>
    
    <!-- Users Table Card -->
    <div class="glass-card rounded-2xl p-6 sm:p-8 animate-fade-in-up" style="animation-delay: 0.1s">
        <div class="flex items-center mb-6">
            <div class="gradient-bg w-12 h-12 rounded-xl flex items-center justify-center mr-4">
                <svg class="w-6 h-6 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z"></path>
                </svg>
            </div>
            <h2 class="text-2xl font-bold text-gray-800">Manage Users</h2>
        </div>
        
        <?php if ($user_list) : ?>
            <div class="overflow-x-auto">
                <table class="w-full">
                    <thead>
                        <tr class="border-b-2 border-gray-200">
                            <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700">ID</th>
                            <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700">Username</th>
                            <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700">Fullname</th>
                            <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700">Level</th>
                            <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700">Saldo</th>
                            <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700">Status</th>
                            <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700">Uplink</th>
                            <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700">Expiration</th>
                            <th class="px-4 py-3 text-left text-sm font-semibold text-gray-700">Action</th>
                        </tr>
                    </thead>
                    <tbody>
                        <?php foreach ($user_list as $u) : ?>
                        <tr class="border-b border-gray-100 hover:bg-gray-50 transition-colors">
                            <td class="px-4 py-3 text-sm text-gray-700"><?= $u->id_users ?></td>
                            <td class="px-4 py-3">
                                <span class="font-semibold text-gray-800"><?= $u->username ?></span>
                            </td>
                            <td class="px-4 py-3 text-sm text-gray-700"><?= $u->fullname ?></td>
                            <td class="px-4 py-3">
                                <?php if($u->level == 1) : ?>
                                    <span class="px-2 py-1 bg-gradient-to-r from-purple-100 to-pink-100 text-purple-700 rounded-lg text-xs font-semibold">
                                        Owner
                                    </span>
                                <?php elseif($u->level == 2) : ?>
                                    <span class="px-2 py-1 bg-gradient-to-r from-blue-100 to-indigo-100 text-blue-700 rounded-lg text-xs font-semibold">
                                        Admin
                                    </span>
                                <?php else : ?>
                                    <span class="px-2 py-1 bg-gradient-to-r from-green-100 to-teal-100 text-green-700 rounded-lg text-xs font-semibold">
                                        Reseller
                                    </span>
                                <?php endif; ?>
                            </td>
                            <td class="px-4 py-3">
                                <?php if($u->level == 1) : ?>
                                    <span class="text-gray-400">∞</span>
                                <?php else : ?>
                                    <span class="px-2 py-1 bg-green-100 text-green-700 rounded-lg text-xs font-semibold">
                                        ₹<?= $u->saldo ?>
                                    </span>
                                <?php endif; ?>
                            </td>
                            <td class="px-4 py-3">
                                <?php if($u->status == 1) : ?>
                                    <span class="px-2 py-1 bg-green-100 text-green-700 rounded-lg text-xs font-semibold">
                                        Active
                                    </span>
                                <?php elseif($u->status == 2) : ?>
                                    <span class="px-2 py-1 bg-red-100 text-red-700 rounded-lg text-xs font-semibold">
                                        Banned
                                    </span>
                                <?php else : ?>
                                    <span class="px-2 py-1 bg-orange-100 text-orange-700 rounded-lg text-xs font-semibold">
                                        Expired
                                    </span>
                                <?php endif; ?>
                            </td>
                            <td class="px-4 py-3 text-sm text-gray-700"><?= $u->uplink ?></td>
                            <td class="px-4 py-3 text-sm text-gray-700"><?= $u->expiration_date ?></td>
                            <td class="px-4 py-3">
                                <a href="user/<?php echo $u->id_users ?>" class="px-3 py-2 bg-gradient-to-r from-purple-600 to-pink-600 text-white rounded-lg hover:from-purple-500 hover:to-pink-500 transition-all inline-flex items-center space-x-1">
                                    <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                                        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z"></path>
                                    </svg>
                                    <span class="text-xs">Edit</span>
                                </a>
                            </td>
                        </tr>
                        <?php endforeach; ?>
                    </tbody>
                </table>
            </div>
        <?php else : ?>
            <p class="text-center text-gray-500 py-8">No users found</p>
        <?php endif; ?>
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

<?= $this->section('css') ?>
<?= link_tag("https://cdn.datatables.net/1.10.25/css/dataTables.bootstrap5.min.css") ?>
<?= $this->endSection() ?>

<?= $this->section('js') ?>
<?= script_tag("https://cdn.datatables.net/1.10.25/js/jquery.dataTables.min.js") ?>
<?= script_tag("https://cdn.datatables.net/1.10.25/js/dataTables.bootstrap5.min.js") ?>
<?= $this->endSection() ?>