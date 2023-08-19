create table user
(
    `id`         bigint auto_increment primary key,
    `name`       varchar(32)            not null,
    `point`      int      default 0     not null,
    `created_at` datetime default now() not null,
    `updated_at` datetime default now() not null,
    `deleted_at` datetime
);

insert into user(name) value ('홍');

create table user_point
(
    `id`         bigint auto_increment primary key,
    `user_id`    bigint                 not null references `user` (`id`),
    `type`       varchar(32)            not null,
    `point`      int                    not null,
    `expired_at` datetime,
    `created_at` datetime default now() not null
);

create table user_point_detail
(
    `id`            binary(32) primary key,
    `user_point_id` bigint                 not null references user_point (id),
    group_id        binary(32)             not null,
    `type`          varchar(32)            not null,
    `point`         int                    not null,
    `created_at`    datetime default now() not null
);